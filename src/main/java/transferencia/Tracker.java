package transferencia;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Tracker {

    private static final List<String> peers = new ArrayList<>();
    private static long tamanhoArquivo = -1;

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 6000;

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("tracker na porta " + porta);
            while (true) {
                Socket socket = servidor.accept();
                new Thread(() -> atender(socket)).start();
            }
        }
    }

    static void atender(Socket socket) {
        try (socket) {
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());

            int portaPeer = entrada.readInt();
            long tamanho = entrada.readLong();
            // o IP vem da própria conexão; o peer informa só a porta em que atende
            String endereco = socket.getInetAddress().getHostAddress() + ":" + portaPeer;

            List<String> outros;
            long tamanhoConhecido;
            synchronized (peers) {
                if (tamanho >= 0) {
                    tamanhoArquivo = tamanho;
                }
                if (!peers.contains(endereco)) {
                    peers.add(endereco);
                    System.out.println("peer registrado: " + endereco);
                }
                outros = new ArrayList<>(peers);
                outros.remove(endereco);
                tamanhoConhecido = tamanhoArquivo;
            }

            saida.writeLong(tamanhoConhecido);
            saida.writeInt(outros.size());
            for (String outro : outros) {
                saida.writeUTF(outro);
            }
            saida.flush();
        } catch (IOException e) {
            System.out.println("erro no tracker: " + e.getMessage());
        }
    }
}
