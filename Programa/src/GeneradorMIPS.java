import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Nombre: GeneradorMIPS
 * Descripcion: Traduce el codigo intermedio a lenguaje ensamblador MIPS.
 * Salidas: Archivo de codigo ensamblador con extension .asm.
 */
public class GeneradorMIPS {

  private final List<String> codigoIntermedio = new ArrayList<>();
  private final List<String> codigoMIPS = new ArrayList<>();
  private final List<String> cadenas = new ArrayList<>();

  /**
   * Nombre: leerCodigoIntermedio
   * Descripcion: Lee las instrucciones del archivo de codigo intermedio.
   * Salidas: Instrucciones almacenadas en memoria.
   *
   * @param archivo Ruta del archivo de codigo intermedio.
   * @throws IOException Si ocurre un error durante la lectura.
   */
  public void leerCodigoIntermedio(String archivo) throws IOException {
    codigoIntermedio.clear();

    codigoIntermedio.addAll(
        Files.readAllLines(
            Path.of(archivo),
            StandardCharsets.UTF_8
        )
    );
  }

  /**
   * Nombre: generar
   * Descripcion: Genera las secciones del codigo ensamblador MIPS,
   * las declaraciones globales y los literales de cadenas.
   * Salidas: Codigo MIPS almacenado en memoria.
   */
  public void generar() {
    codigoMIPS.clear();
    cadenas.clear();

    codigoMIPS.add(".data");

    for (String instruccion : codigoIntermedio) {
      instruccion = instruccion.trim();

      if (instruccion.startsWith("func ")) {
        break;
      }

      if (instruccion.startsWith("data_")) {
        generarDeclaracionGlobal(instruccion);
      }
    }

    generarLiteralesCadena();

    codigoMIPS.add("");
    codigoMIPS.add(".text");
  }

  /**
   * Nombre: generarDeclaracionGlobal
   * Descripcion: Traduce una declaracion global del codigo
   * intermedio a su correspondiente directiva MIPS.
   * Salidas: Declaracion agregada a la seccion de datos.
   *
   * @param instruccion Declaracion del codigo intermedio.
   */
  private void generarDeclaracionGlobal(String instruccion) {
    String[] partes = instruccion.split("\\s+", 2);

    String tipo = partes[0].substring(5);
    String nombre = partes[1];

    if (nombre.contains("[")) {
      generarDeclaracionArregloGlobal(tipo, nombre);
      return;
    }

    switch (tipo) {
      case "int":
      case "bool":
      case "string":
        codigoMIPS.add("    " + nombre + ": .word 0");
        break;

      case "float":
        codigoMIPS.add("    " + nombre + ": .float 0.0");
        break;

      case "char":
        codigoMIPS.add("    " + nombre + ": .byte 0");
        break;
    }
  }

  /**
   * Nombre: generarDeclaracionArregloGlobal
   * Descripcion: Reserva espacio en memoria para un arreglo global.
   * Entradas: Tipo de dato y nombre del arreglo con su dimension.
   * Salidas: Declaracion agregada a la seccion de datos.
   * Restricciones: Solo admite arreglos de tipo int y float
   * con dimensiones constantes positivas.
   *
   * @param tipo Tipo de dato del arreglo.
   * @param declaracion Nombre y dimension del arreglo.
   */
  private void generarDeclaracionArregloGlobal(
      String tipo, String declaracion
  ) {
    if (!tipo.equals("int") && !tipo.equals("float")) {
      return;
    }

    int inicio = declaracion.indexOf("[");
    int fin = declaracion.indexOf("]");

    String nombre = declaracion.substring(0, inicio);

    int dimension = Integer.parseInt(
        declaracion.substring(inicio + 1, fin)
    );

    int espacio = dimension * 4;

    codigoMIPS.add("    .align 2");
    codigoMIPS.add("    " + nombre + ": .space " + espacio);
  }

  /**
   * Nombre: generarLiteralesCadena
   * Descripcion: Busca los literales de cadena en el codigo
   * intermedio y los declara en la seccion de datos.
   * Salidas: Etiquetas .asciiz agregadas a la seccion de datos.
   */
  private void generarLiteralesCadena() {
    for (String instruccion : codigoIntermedio) {
      java.util.regex.Matcher coincidencias =
          java.util.regex.Pattern
              .compile("\"[^\"]*\"")
              .matcher(instruccion);

      while (coincidencias.find()) {
        registrarCadena(coincidencias.group());
      }
    }
  }

  /**
   * Nombre: registrarCadena
   * Descripcion: Registra una cadena literal evitando duplicados.
   * Entradas: Literal de cadena.
   * Salidas: Etiqueta asociada a la cadena.
   *
   * @param literal Cadena que se desea registrar.
   * @return Etiqueta de la cadena en MIPS.
   */
  private String registrarCadena(String literal) {
    int posicion = cadenas.indexOf(literal);

    if (posicion == -1) {
      posicion = cadenas.size();

      cadenas.add(literal);

      codigoMIPS.add("    __cadena" + posicion
          + ": .asciiz " + literal);
    }

    return "__cadena" + posicion;
  }

  /**
   * Nombre: guardar
   * Descripcion: Guarda el codigo MIPS generado en un archivo.
   * Salidas: Archivo de codigo ensamblador.
   *
   * @param archivo Ruta del archivo de salida.
   * @throws IOException Si ocurre un error durante la escritura.
   */
  public void guardar(String archivo) throws IOException {
    Files.write(
        Path.of(archivo),
        codigoMIPS,
        StandardCharsets.UTF_8
    );
  }
}
