package transferencia;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class ServidorSequencial {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("uso: ServidorSequencial <porta> <arquivo>");
            return;
        }
        int porta = Integer.parseInt(args[0]);
        Path arquivo = Path.of(args[1]);

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("servidor sequencial na porta " + porta + ", arquivo " + arquivo);
            while (true) {
                Socket cliente = servidor.accept();
                // envia na mesma thread: os outros clientes esperam na fila do accept
                Transferencia.atender(cliente, arquivo);
            }
        }
    }
}
