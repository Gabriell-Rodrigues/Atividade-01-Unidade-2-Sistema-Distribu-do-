package transferencia;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class GeradorArquivos {

    public static void main(String[] args) throws IOException {
        Path pasta = Path.of(args.length > 0 ? args[0] : "arquivos");
        Files.createDirectories(pasta);

        int[] tamanhos = {5, 50, 500};
        if (args.length > 1) {
            tamanhos = new int[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                tamanhos[i - 1] = Integer.parseInt(args[i]);
            }
        }

        for (int mb : tamanhos) {
            Path arquivo = pasta.resolve("arquivo_" + mb + "MB.bin");
            if (Files.exists(arquivo) && Files.size(arquivo) == mb * 1024L * 1024L) {
                System.out.println(arquivo + " ja existe");
                continue;
            }
            gerar(arquivo, mb);
            System.out.println("gerado " + arquivo + " (" + Files.size(arquivo) + " bytes)");
        }
    }

    static void gerar(Path arquivo, int mb) throws IOException {
        // semente fixa: o arquivo é sempre o mesmo em todas as execuções
        Random aleatorio = new Random(mb);
        byte[] bloco = new byte[1024 * 1024];
        try (OutputStream saida = Files.newOutputStream(arquivo)) {
            for (int i = 0; i < mb; i++) {
                aleatorio.nextBytes(bloco);
                saida.write(bloco);
            }
        }
    }
}
