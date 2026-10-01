package transferencia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Peer {

    static final int TAMANHO_PEDACO = 256 * 1024;

    private final String nome;
    private final String hostTracker;
    private final int portaTracker;
    private final ServerSocket servidor;
    private final Map<String, Conexao> conexoes = new HashMap<>();

    private long tamanho = -1;
    private int totalPedacos;
    private boolean[] pedacos;
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

    void semear(Path caminho) throws IOException {
        tamanho = Files.size(caminho);
        preparar(FileChannel.open(caminho, StandardOpenOption.READ), true);
        registrar();
        System.out.println(nome + " semeando " + caminho + " (" + totalPedacos + " pedacos)");
    }

    void baixar() throws Exception {
        long inicio = System.nanoTime();

        List<String> outros = registrar();
        while (tamanho < 0) {
            Thread.sleep(500);
            outros = registrar();
        }
        Path parcial = Files.createTempFile("peer-" + nome + "-", ".part");
        parcial.toFile().deleteOnExit();
        preparar(FileChannel.open(parcial, StandardOpenOption.READ, StandardOpenOption.WRITE), false);

        for (int i = 0; i < totalPedacos; i++) {
            while (!tem(i)) {
                Collections.shuffle(outros);
                for (String outro : outros) {
                    if (baixarPedaco(outro, i)) {
                        break;
                    }
                }
                if (!tem(i)) {
                    Thread.sleep(200);
                    outros = registrar();
                }
            }
        }

        double segundos = (System.nanoTime() - inicio) / 1e9;
        System.out.println(String.format(Locale.ROOT, "%s recebeu %d bytes em %.3f s", nome, tamanho, segundos));
        System.out.println(nome + " continua compartilhando o arquivo");
    }

    void preparar(FileChannel canal, boolean completo) {
        arquivo = canal;
        totalPedacos = (int) ((tamanho + TAMANHO_PEDACO - 1) / TAMANHO_PEDACO);
        pedacos = new boolean[totalPedacos];
        if (completo) {
            for (int i = 0; i < totalPedacos; i++) {
                pedacos[i] = true;
            }
        }
        new Thread(this::aceitarConexoes).start();
    }

    List<String> registrar() throws IOException {
        try (Socket socket = new Socket(hostTracker, portaTracker)) {
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
            DataInputStream entrada = new DataInputStream(socket.getInputStream());

            saida.writeInt(servidor.getLocalPort());
            saida.writeLong(pedacos != null && todosPedacos() ? tamanho : -1);
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

    boolean baixarPedaco(String endereco, int indice) {
        try {
            Conexao conexao = conexoes.get(endereco);
            if (conexao == null) {
                conexao = new Conexao(endereco);
                conexoes.put(endereco, conexao);
            }
            conexao.saida.writeInt(indice);
            conexao.saida.flush();

            int tamanhoPedaco = conexao.entrada.readInt();
            if (tamanhoPedaco < 0) {
                return false;
            }
            byte[] dados = new byte[tamanhoPedaco];
            conexao.entrada.readFully(dados);
            gravar(indice, dados);
            marcar(indice);
            return true;
        } catch (IOException e) {
            Conexao conexao = conexoes.remove(endereco);
            if (conexao != null) {
                conexao.fechar();
            }
            return false;
        }
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
                int indice = entrada.readInt();
                if (indice < 0 || indice >= totalPedacos || !tem(indice)) {
                    saida.writeInt(-1);
                } else {
                    byte[] dados = ler(indice);
                    saida.writeInt(dados.length);
                    saida.write(dados);
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

    synchronized boolean tem(int indice) {
        return pedacos[indice];
    }

    synchronized void marcar(int indice) {
        pedacos[indice] = true;
    }

    synchronized boolean todosPedacos() {
        for (boolean temPedaco : pedacos) {
            if (!temPedaco) {
                return false;
            }
        }
        return true;
    }

    static class Conexao {
        final Socket socket;
        final DataInputStream entrada;
        final DataOutputStream saida;

        Conexao(String endereco) throws IOException {
            String[] partes = endereco.split(":");
            socket = new Socket(partes[0], Integer.parseInt(partes[1]));
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream(), TAMANHO_PEDACO + 4));
            saida = new DataOutputStream(socket.getOutputStream());
        }

        void fechar() {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("erro ao fechar conexao: " + e.getMessage());
            }
        }
    }
}
