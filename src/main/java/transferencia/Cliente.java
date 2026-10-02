package transferencia;

import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Locale;

public class Cliente {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("uso: Cliente <host> <porta> [nome]");
            return;
        }
        String host = args[0];
        int porta = Integer.parseInt(args[1]);
        String nome = args.length > 2 ? args[2] : "cliente";
        Transferencia.esperarInicio();

        long inicio;
        long recebidos;
        int tentativas = 0;
        while (true) {
            // o tempo conta a partir do pedido de conexão, incluindo a espera na fila do servidor
            inicio = System.nanoTime();
            try (Socket socket = new Socket(host, porta)) {
                recebidos = Transferencia.receber(socket);
                break;
            } catch (ConnectException | UnknownHostException e) {
                // o servidor pode ainda não estar pronto quando o container do cliente sobe
                if (++tentativas == 60) {
                    throw e;
                }
                Thread.sleep(500);
            }
        }
        double segundos = (System.nanoTime() - inicio) / 1e9;

        System.out.println(String.format(Locale.ROOT, "%s recebeu %d bytes em %.3f s", nome, recebidos, segundos));
    }
}
