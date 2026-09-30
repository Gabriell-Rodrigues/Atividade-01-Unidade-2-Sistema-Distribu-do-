package transferencia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class Transferencia {

    static final int TAMANHO_BUFFER = 64 * 1024;

    public static void enviar(Path arquivo, Socket socket) throws IOException {
        DataOutputStream saida = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream(), TAMANHO_BUFFER));
        saida.writeLong(Files.size(arquivo));

        byte[] buffer = new byte[TAMANHO_BUFFER];
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            int lidos;
            while ((lidos = entrada.read(buffer)) != -1) {
                saida.write(buffer, 0, lidos);
            }
        }
        saida.flush();
    }

    public static long receber(Socket socket) throws IOException {
        DataInputStream entrada = new DataInputStream(
                new BufferedInputStream(socket.getInputStream(), TAMANHO_BUFFER));
        long tamanho = entrada.readLong();

        byte[] buffer = new byte[TAMANHO_BUFFER];
        long recebidos = 0;
        while (recebidos < tamanho) {
            int lidos = entrada.read(buffer, 0, (int) Math.min(buffer.length, tamanho - recebidos));
            if (lidos == -1) {
                throw new EOFException("conexao encerrada com " + recebidos + " de " + tamanho + " bytes");
            }
            recebidos += lidos;
        }
        return recebidos;
    }
}
