package transferencia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

public class Peer {

    static final int TAMANHO_PEDACO = 256 * 1024;

    private final String nome;
    private final String hostTracker;
    private final int portaTracker;
    private final ServerSocket servidor;
    private final Random aleatorio = new Random();

    private long tamanho = -1;
    private int totalPedacos;
    private BitSet pedacos;
    private final BitSet emAndamento = new BitSet();
    private final Set<String> conectados = new HashSet<>();
    private final Map<String, Integer> origem = new TreeMap<>();
    private FileChannel arquivo;

    Peer(String nome, String hostTracker, int portaTracker, int porta) throws IOException {
        this.nome = nome;
        this.hostTracker = hostTracker;
        this.portaTracker = portaTracker;
        this.servidor = new ServerSocket(porta);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("uso: Peer <host do tracker> <porta do tracker> <porta> <nome> [arquivo completo]");
            return;
        }
        Peer peer = new Peer(args[3], args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        if (args.length > 4) {
            peer.semear(Path.of(args[4]));
        } else {
            peer.baixar();
        }
    }

    void semear(Path caminho) throws Exception {
        tamanho = Files.size(caminho);
        preparar(FileChannel.open(caminho, StandardOpenOption.READ), true);
        registrarComEspera();
        System.out.println(nome + " semeando " + caminho + " (" + totalPedacos + " pedacos)");
    }

    void baixar() throws Exception {
        Transferencia.esperarInicio();
        List<String> outros = registrarComEspera();
        while (tamanho < 0) {
            Thread.sleep(500);
            outros = registrarComEspera();
        }
        long inicio = System.nanoTime();

        Path parcial = Files.createTempFile("peer-" + nome + "-", ".part");
        parcial.toFile().deleteOnExit();
        preparar(FileChannel.open(parcial, StandardOpenOption.READ, StandardOpenOption.WRITE), false);

        while (true) {
            for (String outro : outros) {
                iniciarDownload(outro);
            }
            synchronized (this) {
                if (!completo()) {
                    wait(1000);
                }
                if (completo()) {
                    break;
                }
            }
            outros = registrarComEspera();
        }

        double segundos = (System.nanoTime() - inicio) / 1e9;
        System.out.println(String.format(Locale.ROOT, "%s recebeu %d bytes em %.3f s", nome, tamanho, segundos));
        synchronized (this) {
            System.out.println(nome + " origem dos pedacos: " + origem);
        }
        System.out.println(nome + " continua compartilhando o arquivo");
    }

    void preparar(FileChannel canal, boolean completo) {
        arquivo = canal;
        totalPedacos = (int) ((tamanho + TAMANHO_PEDACO - 1) / TAMANHO_PEDACO);
        pedacos = new BitSet(totalPedacos);
        if (completo) {
            pedacos.set(0, totalPedacos);
        }
        new Thread(this::aceitarConexoes).start();
    }

    List<String> registrarComEspera() throws Exception {
        int tentativas = 0;
        while (true) {
            try {
                return registrar();
            } catch (ConnectException | UnknownHostException e) {
                if (++tentativas == 60) {
                    throw e;
                }
                Thread.sleep(500);
            }
        }
    }

    List<String> registrar() throws IOException {
        try (Socket socket = new Socket(hostTracker, portaTracker)) {
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
            DataInputStream entrada = new DataInputStream(socket.getInputStream());

            saida.writeInt(servidor.getLocalPort());
            saida.writeLong(pedacos != null && completo() ? tamanho : -1);
            saida.flush();

            long tamanhoInformado = entrada.readLong();
            if (tamanho < 0) {
                tamanho = tamanhoInformado;
            }
            int quantidade = entrada.readInt();
            List<String> outros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                outros.add(entrada.readUTF());
            }
            return outros;
        }
    }

    synchronized void iniciarDownload(String endereco) {
        if (completo() || !conectados.add(endereco)) {
            return;
        }
        new Thread(() -> baixarDe(endereco)).start();
    }

    void baixarDe(String endereco) {
        int atual = -1;
        try (Conexao conexao = new Conexao(endereco)) {
            BitSet doOutro = conexao.pedirMapa();
            while (!completo()) {
                atual = escolherPedaco(doOutro);
                if (atual < 0) {
                    Thread.sleep(100);
                    doOutro = conexao.pedirMapa();
                    continue;
                }
                byte[] dados = conexao.pedirPedaco(atual);
                if (dados == null) {
                    liberar(atual);
                    atual = -1;
                    doOutro = conexao.pedirMapa();
                    continue;
                }
                gravar(atual, dados);
                marcar(atual, endereco);
                atual = -1;
            }
        } catch (IOException | InterruptedException e) {
            if (atual >= 0) {
                liberar(atual);
            }
        } finally {
            synchronized (this) {
                conectados.remove(endereco);
            }
        }
    }

    synchronized int escolherPedaco(BitSet doOutro) {
        List<Integer> candidatos = new ArrayList<>();
        for (int i = doOutro.nextSetBit(0); i >= 0 && i < totalPedacos; i = doOutro.nextSetBit(i + 1)) {
            if (!pedacos.get(i) && !emAndamento.get(i)) {
                candidatos.add(i);
            }
        }
        if (candidatos.isEmpty()) {
            return -1;
        }
        int escolhido = candidatos.get(aleatorio.nextInt(candidatos.size()));
        emAndamento.set(escolhido);
        return escolhido;
    }

    synchronized void marcar(int indice, String endereco) {
        pedacos.set(indice);
        emAndamento.clear(indice);
        origem.merge(endereco, 1, Integer::sum);
        if (completo()) {
            notifyAll();
        }
    }

    synchronized void liberar(int indice) {
        emAndamento.clear(indice);
    }

    synchronized boolean completo() {
        return pedacos.cardinality() == totalPedacos;
    }

    synchronized boolean tem(int indice) {
        return pedacos.get(indice);
    }

    synchronized byte[] mapa() {
        return pedacos.toByteArray();
    }

    void aceitarConexoes() {
        while (true) {
            try {
                Socket socket = servidor.accept();
                new Thread(() -> atender(socket)).start();
            } catch (IOException e) {
                System.out.println(nome + ": erro ao aceitar conexao: " + e.getMessage());
                return;
            }
        }
    }

    void atender(Socket socket) {
        try (socket) {
            DataInputStream entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream saida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), TAMANHO_PEDACO + 4));
            while (true) {
                byte comando = entrada.readByte();
                if (comando == 'M') {
                    byte[] mapa = mapa();
                    saida.writeInt(mapa.length);
                    saida.write(mapa);
                } else if (comando == 'P') {
                    int indice = entrada.readInt();
                    if (indice < 0 || indice >= totalPedacos || !tem(indice)) {
                        saida.writeInt(-1);
                    } else {
                        byte[] dados = ler(indice);
                        saida.writeInt(dados.length);
                        LimiteBanda.escrever(saida, dados, 0, dados.length);
                    }
                } else {
                    throw new IOException("comando desconhecido: " + comando);
                }
                saida.flush();
            }
        } catch (EOFException e) {
            return;
        } catch (IOException e) {
            System.out.println(nome + ": conexao encerrada: " + e.getMessage());
        }
    }

    byte[] ler(int indice) throws IOException {
        long posicao = (long) indice * TAMANHO_PEDACO;
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(TAMANHO_PEDACO, tamanho - posicao));
        while (buffer.hasRemaining()) {
            if (arquivo.read(buffer, posicao + buffer.position()) < 0) {
                throw new EOFException("fim do arquivo no pedaco " + indice);
            }
        }
        return buffer.array();
    }

    void gravar(int indice, byte[] dados) throws IOException {
        long posicao = (long) indice * TAMANHO_PEDACO;
        ByteBuffer buffer = ByteBuffer.wrap(dados);
        while (buffer.hasRemaining()) {
            arquivo.write(buffer, posicao + buffer.position());
        }
    }

    static class Conexao implements AutoCloseable {
        final Socket socket;
        final DataInputStream entrada;
        final DataOutputStream saida;

        Conexao(String endereco) throws IOException {
            String[] partes = endereco.split(":");
            socket = new Socket(partes[0], Integer.parseInt(partes[1]));
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream(), TAMANHO_PEDACO + 4));
            saida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        BitSet pedirMapa() throws IOException {
            saida.writeByte('M');
            saida.flush();
            byte[] mapa = new byte[entrada.readInt()];
            entrada.readFully(mapa);
            return BitSet.valueOf(mapa);
        }

        byte[] pedirPedaco(int indice) throws IOException {
            saida.writeByte('P');
            saida.writeInt(indice);
            saida.flush();
            int tamanhoPedaco = entrada.readInt();
            if (tamanhoPedaco < 0) {
                return null;
            }
            byte[] dados = new byte[tamanhoPedaco];
            entrada.readFully(dados);
            return dados;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
