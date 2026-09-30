package transferencia;

import java.io.IOException;
import java.net.Socket;
import java.util.Locale;

public class Cliente {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("uso: Cliente <host> <porta> [nome]");
            return;
        }
        String host = args[0];
        int porta = Integer.parseInt(args[1]);
        String nome = args.length > 2 ? args[2] : "cliente";

        long inicio = System.nanoTime();
        long recebidos;
        try (Socket socket = new Socket(host, porta)) {
            recebidos = Transferencia.receber(socket);
        }
        double segundos = (System.nanoTime() - inicio) / 1e9;

        System.out.println(String.format(Locale.ROOT, "%s recebeu %d bytes em %.3f s", nome, recebidos, segundos));
    }
}
