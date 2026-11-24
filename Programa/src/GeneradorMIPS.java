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
  private String funcionActual = null;
  private int espacioPila = 0;
  private final List<String> nombresLocales = new ArrayList<>();
  private final List<String> tiposLocales = new ArrayList<>();
  private final List<Integer> desplazamientosLocales = new ArrayList<>();

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
   * Descripcion: Genera las secciones del codigo ensamblador MIPS.
   * Salidas: Codigo MIPS almacenado en memoria.
   */
  public void generar() {
    codigoMIPS.clear();
    cadenas.clear();

    generarSeccionData();
    generarSeccionText();
  }

  /**
   * Nombre: generarSeccionData
   * Descripcion: Genera las declaraciones globales
   * y los literales de cadenas.
   * Salidas: Seccion de datos del codigo MIPS.
   */
  private void generarSeccionData() {
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
  }

  /**
   * Nombre: generarSeccionText
   * Descripcion: Genera la seccion de instrucciones MIPS
   * y procesa el inicio y final de las funciones.
   * Salidas: Seccion de instrucciones del codigo MIPS.
   */
  private void generarSeccionText() {
    codigoMIPS.add("");
    codigoMIPS.add(".text");
    codigoMIPS.add(".globl main");

    for (String instruccion : codigoIntermedio) {
      instruccion = instruccion.trim();

      if (instruccion.startsWith("func ")) {
        generarInicioFuncion(instruccion);
      } else if (instruccion.startsWith("endfunc ")) {
        generarFinFuncion();
      }
    }
  }

  /**
   * Nombre: calcularEspacioPila
   * Descripcion: Registra las variables locales y calcula
   * el espacio necesario para el marco de pila.
   * Entradas: Nombre de la funcion.
   * Salidas: Cantidad de bytes necesarios.
   *
   * @param nombre Nombre de la funcion.
   * @return Tamano del marco de pila.
   */
  private int calcularEspacioPila(String nombre) {
    int espacio = 0;
    boolean dentroFuncion = false;

    nombresLocales.clear();
    tiposLocales.clear();
    desplazamientosLocales.clear();

    for (String instruccion : codigoIntermedio) {
      instruccion = instruccion.trim();

      if (instruccion.equals("func " + nombre + ":")) {
        dentroFuncion = true;
        continue;
      }

      if (!dentroFuncion) {
        continue;
      }

      if (instruccion.startsWith("endfunc ")) {
        break;
      }

      if (instruccion.startsWith("data_")) {
        espacio += registrarVariableLocal(instruccion, espacio);
      }
    }

    espacio += 4;

    while (espacio % 8 != 0) {
      espacio++;
    }

    return espacio;
  }

  /**
   * Nombre: registrarVariableLocal
   * Descripcion: Registra el nombre, tipo y desplazamiento
   * de una variable local.
   * Entradas: Declaracion y desplazamiento de la variable.
   * Salidas: Cantidad de bytes que ocupa la variable.
   *
   * @param instruccion Declaracion del codigo intermedio.
   * @param desplazamiento Posicion de la variable en la pila.
   * @return Espacio ocupado por la variable.
   */
  private int registrarVariableLocal(String instruccion,
                                     int desplazamiento) {
    String[] partes = instruccion.split("\\s+", 2);

    String tipo = partes[0].substring(5);
    String nombre = partes[1];

    int espacio = 4;

    if (nombre.contains("[")) {
      espacio = obtenerDimension(nombre) * 4;
      nombre = nombre.substring(0, nombre.indexOf("["));
    }

    nombresLocales.add(nombre);
    tiposLocales.add(tipo);
    desplazamientosLocales.add(desplazamiento);

    return espacio;
  }

  /**
   * Nombre: obtenerDimension
   * Descripcion: Extrae la dimension de un arreglo.
   * Entradas: Declaracion del arreglo.
   * Salidas: Cantidad de elementos del arreglo.
   *
   * @param declaracion Declaracion del arreglo.
   * @return Dimension del arreglo.
   */
  private int obtenerDimension(String declaracion) {
    int inicio = declaracion.indexOf("[");
    int fin = declaracion.indexOf("]");

    return Integer.parseInt(
        declaracion.substring(inicio + 1, fin)
    );
  }

  /**
   * Nombre: generarInicioFuncion
   * Descripcion: Genera la etiqueta y el prologo de una funcion.
   * Entradas: Instruccion de inicio de funcion.
   * Salidas: Etiqueta y reserva del marco de pila.
   *
   * @param instruccion Inicio de funcion del codigo intermedio.
   */
  private void generarInicioFuncion(String instruccion) {
    funcionActual = instruccion.substring(5)
        .replace(":", "").trim();

    espacioPila = calcularEspacioPila(funcionActual);

    codigoMIPS.add("");

    if (funcionActual.equals("principal")) {
      codigoMIPS.add("main:");
    } else {
      codigoMIPS.add(funcionActual + ":");
    }

    codigoMIPS.add("    addiu $sp, $sp, -" + espacioPila);

    codigoMIPS.add("    sw $ra, "
        + (espacioPila - 4) + "($sp)");
  }

  /**
   * Nombre: generarFinFuncion
   * Descripcion: Genera el epilogo y la finalizacion
   * de la funcion actual.
   * Salidas: Restauracion del marco de pila y retorno.
   */
  private void generarFinFuncion() {
    codigoMIPS.add("    lw $ra, "
        + (espacioPila - 4) + "($sp)");

    codigoMIPS.add("    addiu $sp, $sp, " + espacioPila);

    if (funcionActual.equals("principal")) {
      codigoMIPS.add("    li $v0, 10");
      codigoMIPS.add("    syscall");
    } else {
      codigoMIPS.add("    jr $ra");
      codigoMIPS.add("    nop");
    }

    funcionActual = null;
    espacioPila = 0;
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

    String nombre = declaracion.substring(
        0, declaracion.indexOf("[")
    );

    int espacio = obtenerDimension(declaracion) * 4;

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
      int inicio = instruccion.indexOf("\"");
      int fin = instruccion.lastIndexOf("\"");

      if (inicio != -1 && fin > inicio) {
        String literal = instruccion.substring(inicio, fin + 1);

        registrarCadena(literal);
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
