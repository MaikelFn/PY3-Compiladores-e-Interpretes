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
  private boolean utilizaPotencia = false;
  private boolean utilizaComparacionFloat = false;
  private final List<String> nombresLocales = new ArrayList<>();
  private final List<String> tiposLocales = new ArrayList<>();
  private final List<Integer> desplazamientosLocales = new ArrayList<>();
  private final List<String> nombresGlobales = new ArrayList<>();
  private final List<String> tiposGlobales = new ArrayList<>();
  private final List<String> nombresTemporales = new ArrayList<>();
  private final List<String> tiposTemporales = new ArrayList<>();
  private final List<String> registrosTemporales = new ArrayList<>();

  private final String[] registrosEnteros = {
      "$t0", "$t1", "$t2", "$t3",
      "$t4", "$t5", "$t6", "$t7"
  };

  private final String[] registrosFlotantes = {
      "$f2", "$f4", "$f6", "$f8",
      "$f10", "$f14", "$f16", "$f18"
  };

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

    nombresGlobales.clear();
    tiposGlobales.clear();

    nombresTemporales.clear();
    tiposTemporales.clear();
    registrosTemporales.clear();

    utilizaPotencia = false;
    utilizaComparacionFloat = false;

    generarSeccionData();
    generarSeccionText();

    if (utilizaPotencia) {
      generarSubrutinaPotencia();
    }

    if (utilizaComparacionFloat) {
      generarSubrutinaComparacionFloat();
    }
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

    for (int i = 0; i < codigoIntermedio.size(); i++) {
      String instruccion = codigoIntermedio.get(i).trim();

      if (instruccion.startsWith("func ")) {
        generarInicioFuncion(instruccion);

      } else if (instruccion.startsWith("endfunc ")) {
        generarFinFuncion();

      } else if (instruccion.startsWith("temp_")) {
        registrarTemporal(instruccion);

      } else if (funcionActual != null
          && instruccion.contains(" = ")) {

        String[] partes = instruccion.split(" = ", 2);

        String destino = partes[0].trim();
        String origen = partes[1].trim();

        if (nombresTemporales.contains(destino)
            && esLiteral(origen)) {

          generarAsignacionLiteral(destino, origen);

        } else if ((esVariableLocal(destino)
                || esVariableGlobal(destino))
            && nombresTemporales.contains(origen)) {

          generarAsignacionDesdeTemporal(destino, origen);

        } else if ((esVariableLocal(destino)
                || esVariableGlobal(destino))
            && (esVariableLocal(origen)
                || esVariableGlobal(origen))) {

          generarAsignacionEntreVariables(destino, origen);

        } else if (nombresTemporales.contains(destino)
            && (esVariableLocal(origen)
                || esVariableGlobal(origen))) {

          generarAsignacionDesdeVariable(destino, origen);

        } else if (nombresTemporales.contains(destino)
            && (origen.contains(" + ")
                || origen.contains(" - ")
                || origen.contains(" * ")
                || origen.contains(" / ")
                || origen.contains(" // ")
                || origen.contains(" mod ")
                || origen.contains(" pot "))) {

          String[] operandos = origen.split("\\s+", 3);

          generarOperacionAritmetica(
              destino,
              operandos[0],
              operandos[1],
              operandos[2]
          );

        } else if (nombresTemporales.contains(destino)
            && (origen.contains(" > ")
                || origen.contains(" < ")
                || origen.contains(" >= ")
                || origen.contains(" <= ")
                || origen.contains(" == ")
                || origen.contains(" != "))) {

          String[] operandos = origen.split("\\s+", 3);

          generarOperacionRelacional(
              destino,
              operandos[0],
              operandos[1],
              operandos[2]
          );

        } else if (nombresTemporales.contains(destino)
            && (origen.contains(" λ ")
                || origen.contains(" θ "))) {

          String[] operandos = origen.split("\\s+", 3);

          generarOperacionLogica(
              destino,
              operandos[0],
              operandos[1],
              operandos[2]
          );

        } else if (nombresTemporales.contains(destino)
            && origen.startsWith("Σ ")) {

          String operando = origen.substring(2).trim();

          generarNegacionLogica(destino, operando);
        }
      }

      liberarTemporalesSinUso(i);
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
  private int registrarVariableLocal(String instruccion, int desplazamiento) {
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
   * Nombre: esVariableLocal
   * Descripcion: Verifica si una variable pertenece
   * a la funcion actual.
   * Entradas: Nombre de la variable.
   * Salidas: Resultado de la busqueda.
   *
   * @param nombre Nombre de la variable.
   * @return true si la variable es local, false en caso contrario.
   */
  private boolean esVariableLocal(String nombre) {
    return funcionActual != null
        && nombresLocales.contains(nombre);
  }

  /**
   * Nombre: obtenerTipoLocal
   * Descripcion: Obtiene el tipo de dato de una variable local.
   * Entradas: Nombre de la variable.
   * Salidas: Tipo de dato de la variable.
   *
   * @param nombre Nombre de la variable.
   * @return Tipo de dato o null si la variable no existe.
   */
  private String obtenerTipoLocal(String nombre) {
    int posicion = nombresLocales.indexOf(nombre);

    if (posicion == -1) {
      return null;
    }

    return tiposLocales.get(posicion);
  }

  /**
   * Nombre: obtenerDesplazamientoLocal
   * Descripcion: Obtiene el desplazamiento de una variable
   * local dentro del marco de pila.
   * Entradas: Nombre de la variable.
   * Salidas: Desplazamiento respecto al registro $sp.
   *
   * @param nombre Nombre de la variable.
   * @return Desplazamiento o -1 si la variable no existe.
   */
  private int obtenerDesplazamientoLocal(String nombre) {
    int posicion = nombresLocales.indexOf(nombre);

    if (posicion == -1) {
      return -1;
    }

    return desplazamientosLocales.get(posicion);
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

    boolean esArreglo = nombre.contains("[");

    if (esArreglo) {
      generarDeclaracionArregloGlobal(tipo, nombre);
      nombre = nombre.substring(0, nombre.indexOf("["));
    }

    nombresGlobales.add(nombre);
    tiposGlobales.add(tipo);

    if (esArreglo) {
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
  private void generarDeclaracionArregloGlobal(String tipo, String declaracion) {
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
   * Nombre: esVariableGlobal
   * Descripcion: Verifica si una variable es global.
   * Entradas: Nombre de la variable.
   * Salidas: Resultado de la busqueda.
   *
   * @param nombre Nombre de la variable.
   * @return true si la variable es global, false en caso contrario.
   */
  private boolean esVariableGlobal(String nombre) {
    return nombresGlobales.contains(nombre);
  }

  /**
   * Nombre: obtenerTipoGlobal
   * Descripcion: Obtiene el tipo de dato de una variable global.
   * Entradas: Nombre de la variable.
   * Salidas: Tipo de dato de la variable.
   *
   * @param nombre Nombre de la variable.
   * @return Tipo de dato o null si la variable no existe.
   */
  private String obtenerTipoGlobal(String nombre) {
    int posicion = nombresGlobales.indexOf(nombre);

    if (posicion == -1) {
      return null;
    }

    return tiposGlobales.get(posicion);
  }

  /**
   * Nombre: esLiteral
   * Descripcion: Verifica si un operando corresponde
   * a un valor literal del codigo intermedio.
   * Entradas: Operando que se desea verificar.
   * Salidas: Resultado de la verificacion.
   *
   * @param operando Operando del codigo intermedio.
   * @return true si es literal, false en caso contrario.
   */
  private boolean esLiteral(String operando) {
    return operando.equals("true")
        || operando.equals("false")
        || operando.matches("-?[0-9]+")
        || operando.matches("-?[0-9]+\\.[0-9]+")
        || operando.matches("\"[^\"]*\"")
        || operando.matches("'[^']'");
  }

  /**
   * Nombre: obtenerTipoOperando
   * Descripcion: Identifica el tipo de dato de un temporal,
   * variable local, variable global o literal.
   * Entradas: Operando del codigo intermedio.
   * Salidas: Tipo de dato del operando.
   *
   * @param operando Operando que se desea consultar.
   * @return Tipo de dato del operando.
   */
  private String obtenerTipoOperando(String operando) {
    int posicion = nombresTemporales.indexOf(operando);

    if (posicion != -1) {
      return tiposTemporales.get(posicion);
    }

    if (esVariableLocal(operando)) {
      return obtenerTipoLocal(operando);
    }

    if (esVariableGlobal(operando)) {
      return obtenerTipoGlobal(operando);
    }

    if (operando.equals("true") || operando.equals("false")) {
      return "bool";
    }

    if (operando.startsWith("\"") && operando.endsWith("\"")) {
      return "string";
    }

    if (operando.startsWith("'") && operando.endsWith("'")) {
      return "char";
    }

    if (operando.matches("-?[0-9]+")) {
      return "int";
    }

    if (operando.matches("-?[0-9]+\\.[0-9]+")) {
      return "float";
    }

    throw new IllegalArgumentException(
        "Operando no reconocido: " + operando
    );
  }

  /**
   * Nombre: cargarLiteral
   * Descripcion: Genera la instruccion MIPS necesaria
   * para cargar un literal en un registro.
   * Entradas: Literal y registro destino.
   * Salidas: Instruccion de carga en codigo MIPS.
   *
   * @param literal Valor literal del codigo intermedio.
   * @param registro Registro MIPS destino.
   */
  private void cargarLiteral(String literal, String registro) {
    String tipo = obtenerTipoOperando(literal);

    switch (tipo) {
      case "int":
        codigoMIPS.add("    li " + registro + ", " + literal);
        break;

      case "float":
        codigoMIPS.add("    li.s " + registro + ", " + literal);
        break;

      case "bool":
        int valor = literal.equals("true") ? 1 : 0;
        codigoMIPS.add("    li " + registro + ", " + valor);
        break;

      case "char":
        int caracter = literal.charAt(1);
        codigoMIPS.add("    li " + registro + ", " + caracter);
        break;

      case "string":
        String etiqueta = registrarCadena(literal);
        codigoMIPS.add("    la " + registro + ", " + etiqueta);
        break;
    }
  }

  /**
   * Nombre: generarAsignacionLiteral
   * Descripcion: Asigna un registro a un temporal
   * y carga el valor literal correspondiente.
   * Entradas: Nombre del temporal y valor literal.
   * Salidas: Instruccion de asignacion en codigo MIPS.
   *
   * @param temporal Nombre del temporal.
   * @param literal Valor que se desea asignar.
   */
  private void generarAsignacionLiteral(String temporal, String literal) {
    String registro = asignarRegistroTemporal(temporal);

    if (registro == null) {
      throw new IllegalStateException(
          "No hay registros disponibles para: " + temporal
      );
    }

    cargarLiteral(literal, registro);
  }

  /**
   * Nombre: generarAsignacionDesdeTemporal
   * Descripcion: Almacena el valor de un temporal
   * en una variable local o global.
   * Entradas: Variable destino y temporal origen.
   * Salidas: Instruccion de almacenamiento en MIPS.
   *
   * @param destino Nombre de la variable destino.
   * @param temporal Nombre del temporal origen.
   */
  private void generarAsignacionDesdeTemporal(String destino, String temporal) {
    int posicion = nombresTemporales.indexOf(temporal);

    String registro = registrosTemporales.get(posicion);

    if (registro == null) {
      throw new IllegalStateException(
          "Temporal sin registro asignado: " + temporal
      );
    }

    almacenarVariable(destino, registro);
  }

  /**
   * Nombre: generarAsignacionEntreVariables
   * Descripcion: Copia el valor de una variable
   * local o global hacia otra variable.
   * Entradas: Variable destino y variable origen.
   * Salidas: Instrucciones de carga y almacenamiento MIPS.
   *
   * @param destino Nombre de la variable destino.
   * @param origen Nombre de la variable origen.
   */
  private void generarAsignacionEntreVariables(String destino, String origen) {
    String tipo = obtenerTipoOperando(origen);

    String registro = tipo.equals("float") ? "$f20" : "$t8";

    cargarVariable(origen, registro);
    almacenarVariable(destino, registro);
  }

  /**
   * Nombre: generarAsignacionDesdeVariable
   * Descripcion: Carga el valor de una variable
   * local o global en el registro de un temporal.
   * Entradas: Temporal destino y variable origen.
   * Salidas: Instruccion de carga en MIPS.
   *
   * @param temporal Nombre del temporal destino.
   * @param origen Nombre de la variable origen.
   */
  private void generarAsignacionDesdeVariable(String temporal, String origen) {
    String registro = asignarRegistroTemporal(temporal);

    if (registro == null) {
      throw new IllegalStateException(
          "No hay registros disponibles para: " + temporal
      );
    }

    cargarVariable(origen, registro);
  }

  /**
   * Nombre: obtenerRegistroOperando
   * Descripcion: Obtiene el registro que contiene
   * el valor de un temporal, variable o literal.
   * Entradas: Operando y registros auxiliares.
   * Salidas: Registro MIPS que contiene el operando.
   *
   * @param operando Operando del codigo intermedio.
   * @param registroEntero Registro auxiliar entero.
   * @param registroFloat Registro auxiliar flotante.
   * @return Registro que contiene el operando.
   */
  private String obtenerRegistroOperando(String operando,
                                         String registroEntero,
                                         String registroFloat) {
    int posicion = nombresTemporales.indexOf(operando);

    if (posicion != -1) {
      String registro = registrosTemporales.get(posicion);

      if (registro == null) {
        throw new IllegalStateException(
            "Temporal sin registro asignado: " + operando
        );
      }

      return registro;
    }

    String tipo = obtenerTipoOperando(operando);

    String registro = tipo.equals("float")
        ? registroFloat : registroEntero;

    if (esLiteral(operando)) {
      cargarLiteral(operando, registro);
    } else {
      cargarVariable(operando, registro);
    }

    return registro;
  }

  /**
   * Nombre: convertirEnteroAFloat
   * Descripcion: Convierte un valor entero almacenado
   * en un registro a un valor de punto flotante.
   * Salidas: Instrucciones de conversion MIPS.
   *
   * @param registroEntero Registro que contiene el entero.
   * @param registroFloat Registro destino de la conversion.
   * @return Registro flotante que contiene el resultado.
   */
  private String convertirEnteroAFloat(
      String registroEntero,
      String registroFloat
  ) {
    codigoMIPS.add("    mtc1 " + registroEntero
        + ", " + registroFloat);

    codigoMIPS.add("    cvt.s.w " + registroFloat
        + ", " + registroFloat);

    return registroFloat;
  }

  /**
   * Nombre: generarPotencia
   * Descripcion: Genera una llamada a la subrutina
   * encargada de calcular potencias enteras.
   * Entradas: Registros del destino, base y exponente.
   * Salidas: Instrucciones MIPS para llamar a la subrutina.
   * Restricciones: El exponente debe ser mayor que cero.
   *
   * @param registroDestino Registro del resultado.
   * @param registroBase Registro de la base.
   * @param registroExponente Registro del exponente.
   */
  private void generarPotencia(
      String registroDestino,
      String registroBase,
      String registroExponente
  ) {
    utilizaPotencia = true;

    codigoMIPS.add("    move $a0, " + registroBase);
    codigoMIPS.add("    move $a1, " + registroExponente);

    codigoMIPS.add("    jal __potencia");
    codigoMIPS.add("    nop");

    codigoMIPS.add("    move " + registroDestino + ", $v0");
  }

  /**
   * Nombre: generarSubrutinaPotencia
   * Descripcion: Genera una subrutina reutilizable para
   * calcular potencias mediante multiplicaciones sucesivas.
   * Entradas: Base en $a0 y exponente en $a1.
   * Salidas: Resultado en $v0.
   * Restricciones: El exponente debe ser mayor que cero.
   */
  private void generarSubrutinaPotencia() {
    codigoMIPS.add("");
    codigoMIPS.add("__potencia:");

    codigoMIPS.add("    blez $a1, __potencia_error");
    codigoMIPS.add("    nop");

    codigoMIPS.add("    li $v0, 1");

    codigoMIPS.add("__potencia_inicio:");

    codigoMIPS.add("    mul $v0, $v0, $a0");
    codigoMIPS.add("    addiu $a1, $a1, -1");

    codigoMIPS.add("    bgtz $a1, __potencia_inicio");
    codigoMIPS.add("    nop");

    codigoMIPS.add("    jr $ra");
    codigoMIPS.add("    nop");

    codigoMIPS.add("__potencia_error:");
    codigoMIPS.add("    li $v0, 10");
    codigoMIPS.add("    syscall");
  }

  /**
   * Nombre: generarOperacionAritmetica
   * Descripcion: Traduce una operacion aritmetica
   * del codigo intermedio a instrucciones MIPS.
   * Entradas: Temporal destino, operandos y operador.
   * Salidas: Instrucciones aritmeticas MIPS.
   *
   * @param destino Temporal que almacena el resultado.
   * @param operando1 Primer operando.
   * @param operador Operador aritmetico.
   * @param operando2 Segundo operando.
   */
  private void generarOperacionAritmetica(String destino, String operando1, String operador, String operando2) {
    String registro1 = obtenerRegistroOperando(
        operando1, "$t8", "$f20"
    );

    String registro2 = obtenerRegistroOperando(
        operando2, "$t9", "$f22"
    );

    String registroDestino = asignarRegistroTemporal(destino);

    if (registroDestino == null) {
      throw new IllegalStateException(
          "No hay registros disponibles para: " + destino
      );
    }

    String tipo = obtenerTipoOperando(destino);

    if (tipo.equals("float")) {
      if (obtenerTipoOperando(operando1).equals("int")) {
        registro1 = convertirEnteroAFloat(registro1, "$f20");
      }

      if (obtenerTipoOperando(operando2).equals("int")) {
        registro2 = convertirEnteroAFloat(registro2, "$f22");
      }
    }

    String operacion;

    switch (operador) {
      case "+":
        operacion = tipo.equals("float") ? "add.s" : "addu";
        break;

      case "-":
        operacion = tipo.equals("float") ? "sub.s" : "subu";
        break;

      case "*":
        operacion = tipo.equals("float") ? "mul.s" : "mul";
        break;

      case "/":
        operacion = "div.s";
        break;

      case "//":
      case "mod":
        codigoMIPS.add("    div " + registro1
            + ", " + registro2);

        operacion = operador.equals("//") ? "mflo" : "mfhi";

        codigoMIPS.add("    " + operacion + " " + registroDestino);

        return;

      case "pot":
        generarPotencia(
            registroDestino,
            registro1,
            registro2
        );

        return;

      default:
        throw new IllegalArgumentException(
            "Operador no implementado: " + operador
        );
    }

    codigoMIPS.add("    " + operacion + " "
        + registroDestino + ", "
        + registro1 + ", "
        + registro2);
  }

  /**
   * Nombre: generarOperacionRelacional
   * Descripcion: Genera instrucciones MIPS para
   * comparar dos operandos del mismo tipo.
   * Entradas: Temporal destino, operandos y operador.
   * Salidas: Instrucciones MIPS de comparacion.
   * Restricciones: Los operandos deben ser del mismo
   * tipo y compatibles con el operador relacional.
   */
  private void generarOperacionRelacional(
      String destino,
      String operando1,
      String operador,
      String operando2
  ) {
    String registro1 = obtenerRegistroOperando(
        operando1, "$t8", "$f20"
    );

    String registro2 = obtenerRegistroOperando(
        operando2, "$t9", "$f22"
    );

    String registroDestino = asignarRegistroTemporal(destino);

    if (registroDestino == null) {
      throw new IllegalStateException(
          "No hay registros disponibles para: " + destino
      );
    }

    String tipo = obtenerTipoOperando(operando1);

    if (tipo.equals("float")) {
      if (operador.equals(">") || operador.equals(">=")) {
        String auxiliar = registro1;
        registro1 = registro2;
        registro2 = auxiliar;
      }

      generarComparacionFloat(
          registroDestino, registro1, registro2, operador
      );

    } else {
      switch (operador) {
        case "==":
          codigoMIPS.add("    xor " + registroDestino
              + ", " + registro1 + ", " + registro2);

          codigoMIPS.add("    sltiu " + registroDestino
              + ", " + registroDestino + ", 1");
          break;

        case "!=":
          codigoMIPS.add("    xor " + registroDestino
              + ", " + registro1 + ", " + registro2);

          codigoMIPS.add("    sltu " + registroDestino
              + ", $zero, " + registroDestino);
          break;

        case ">":
        case "<":
        case ">=":
        case "<=":
          if (operador.equals(">") || operador.equals("<=")) {
            String auxiliar = registro1;
            registro1 = registro2;
            registro2 = auxiliar;
          }

          codigoMIPS.add("    slt " + registroDestino
              + ", " + registro1 + ", " + registro2);

          if (operador.equals(">=") || operador.equals("<=")) {
            codigoMIPS.add("    xori " + registroDestino
                + ", " + registroDestino + ", 1");
          }
          break;

        default:
          throw new IllegalArgumentException(
              "Operador relacional no reconocido: " + operador
          );
      }
    }
  }

  /**
   * Nombre: generarOperacionLogica
   * Descripcion: Genera instrucciones MIPS para
   * operaciones logicas binarias.
   * Entradas: Temporal destino, operandos y operador.
   * Salidas: Instrucciones MIPS and u or.
   * Restricciones: Los operandos deben ser booleanos.
   */
  private void generarOperacionLogica(
      String destino,
      String operando1,
      String operador,
      String operando2
  ) {
    String registro1 = obtenerRegistroOperando(
        operando1, "$t8", "$f20"
    );

    String registro2 = obtenerRegistroOperando(
        operando2, "$t9", "$f22"
    );

    String registroDestino = asignarRegistroTemporal(destino);

    if (registroDestino == null) {
      throw new IllegalStateException(
          "No hay registros disponibles para: " + destino
      );
    }

    String operacion;

    switch (operador) {
      case "λ":
        operacion = "and";
        break;

      case "θ":
        operacion = "or";
        break;

      default:
        throw new IllegalArgumentException(
            "Operador logico no reconocido: " + operador
        );
    }

    codigoMIPS.add("    " + operacion + " "
        + registroDestino + ", "
        + registro1 + ", " + registro2);
  }

  /**
   * Nombre: generarNegacionLogica
   * Descripcion: Invierte el valor de un booleano.
   * Entradas: Temporal destino y operando.
   * Salidas: Instruccion MIPS xori.
   * Restricciones: El operando debe ser booleano.
   */
  private void generarNegacionLogica(
      String destino,
      String operando
  ) {
    String registro = obtenerRegistroOperando(
        operando, "$t8", "$f20"
    );

    String registroDestino = asignarRegistroTemporal(destino);

    if (registroDestino == null) {
      throw new IllegalStateException(
          "No hay registros disponibles para: " + destino
      );
    }

    codigoMIPS.add("    xori " + registroDestino
        + ", " + registro + ", 1");
  }

  /**
   * Nombre: generarComparacionFloat
   * Descripcion: Genera la llamada a la subrutina
   * correspondiente para comparar flotantes.
   * Entradas: Registro destino, registros operandos
   * y operador relacional.
   * Salidas: Instrucciones MIPS para realizar la llamada.
   */
  private void generarComparacionFloat(
      String registroDestino,
      String registro1,
      String registro2,
      String operador
  ) {
    utilizaComparacionFloat = true;

    codigoMIPS.add("    mov.s $f12, " + registro1);
    codigoMIPS.add("    mov.s $f24, " + registro2);

    switch (operador) {
      case ">":
      case "<":
        codigoMIPS.add("    jal __compararFloat");
        break;

      case ">=":
      case "<=":
        codigoMIPS.add("    jal __compararFloatIgual");
        break;

      case "==":
        codigoMIPS.add("    jal __igualdadFloat");
        break;

      case "!=":
        codigoMIPS.add("    jal __diferenciaFloat");
        break;

      default:
        throw new IllegalArgumentException(
            "Operador relacional no reconocido: " + operador
        );
    }

    codigoMIPS.add("    nop");

    codigoMIPS.add("    move " + registroDestino + ", $v0");
  }

  /**
   * Nombre: generarSubrutinaComparacionFloat
   * Descripcion: Genera las subrutinas independientes
   * para realizar comparaciones entre flotantes.
   * Entradas: Registros $f12 y $f24.
   * Salidas: Resultado booleano en $v0.
   */
  private void generarSubrutinaComparacionFloat() {
    codigoMIPS.add("");
    codigoMIPS.add("__compararFloat:");

    codigoMIPS.add("    li $v0, 0");
    codigoMIPS.add("    c.lt.s $f12, $f24");
    codigoMIPS.add("    bc1f __compararFloat_fin");
    codigoMIPS.add("    nop");
    codigoMIPS.add("    li $v0, 1");

    codigoMIPS.add("__compararFloat_fin:");
    codigoMIPS.add("    jr $ra");
    codigoMIPS.add("    nop");

    codigoMIPS.add("");
    codigoMIPS.add("__compararFloatIgual:");

    codigoMIPS.add("    li $v0, 0");
    codigoMIPS.add("    c.le.s $f12, $f24");
    codigoMIPS.add("    bc1f __compararFloatIgual_fin");
    codigoMIPS.add("    nop");
    codigoMIPS.add("    li $v0, 1");

    codigoMIPS.add("__compararFloatIgual_fin:");
    codigoMIPS.add("    jr $ra");
    codigoMIPS.add("    nop");

    codigoMIPS.add("");
    codigoMIPS.add("__igualdadFloat:");

    codigoMIPS.add("    li $v0, 0");
    codigoMIPS.add("    c.eq.s $f12, $f24");
    codigoMIPS.add("    bc1f __igualdadFloat_fin");
    codigoMIPS.add("    nop");
    codigoMIPS.add("    li $v0, 1");

    codigoMIPS.add("__igualdadFloat_fin:");
    codigoMIPS.add("    jr $ra");
    codigoMIPS.add("    nop");

    codigoMIPS.add("");
    codigoMIPS.add("__diferenciaFloat:");

    codigoMIPS.add("    li $v0, 0");
    codigoMIPS.add("    c.eq.s $f12, $f24");
    codigoMIPS.add("    bc1t __diferenciaFloat_fin");
    codigoMIPS.add("    nop");
    codigoMIPS.add("    li $v0, 1");

    codigoMIPS.add("__diferenciaFloat_fin:");
    codigoMIPS.add("    jr $ra");
    codigoMIPS.add("    nop");
  }

  /**
   * Nombre: cargarVariable
   * Descripcion: Carga el valor de una variable local
   * o global en un registro MIPS.
   * Entradas: Nombre de la variable y registro destino.
   * Salidas: Instruccion de carga en codigo MIPS.
   *
   * @param nombre Nombre de la variable.
   * @param registro Registro destino.
   */
  private void cargarVariable(String nombre, String registro) {
    accederVariable(nombre, registro, true);
  }

  /**
   * Nombre: almacenarVariable
   * Descripcion: Almacena el contenido de un registro
   * MIPS en una variable local o global.
   * Entradas: Nombre de la variable y registro origen.
   * Salidas: Instruccion de almacenamiento en codigo MIPS.
   *
   * @param nombre Nombre de la variable.
   * @param registro Registro origen.
   */
  private void almacenarVariable(String nombre, String registro) {
    accederVariable(nombre, registro, false);
  }

  /**
   * Nombre: accederVariable
   * Descripcion: Genera una instruccion de carga o
   * almacenamiento para una variable local o global.
   * Entradas: Nombre, registro y tipo de acceso.
   * Salidas: Instruccion de acceso a memoria.
   *
   * @param nombre Nombre de la variable.
   * @param registro Registro MIPS.
   * @param cargar true para cargar, false para almacenar.
   */
  private void accederVariable(String nombre, String registro, boolean cargar) {
    String tipo;
    String direccion;
    String operacion;

    boolean local = esVariableLocal(nombre);

    if (local) {
      tipo = obtenerTipoLocal(nombre);
      direccion = obtenerDesplazamientoLocal(nombre) + "($sp)";

    } else if (esVariableGlobal(nombre)) {
      tipo = obtenerTipoGlobal(nombre);
      direccion = nombre;

    } else {
      throw new IllegalArgumentException(
          "Variable no registrada: " + nombre
      );
    }

    if (tipo.equals("float")) {
      operacion = cargar ? "l.s" : "s.s";

    } else if (tipo.equals("char") && !local) {
      operacion = cargar ? "lbu" : "sb";

    } else {
      operacion = cargar ? "lw" : "sw";
    }

    codigoMIPS.add("    " + operacion + " "
        + registro + ", " + direccion);
  }

  /**
   * Nombre: registrarTemporal
   * Descripcion: Registra el nombre y tipo de un temporal
   * del codigo intermedio.
   * Entradas: Declaracion del temporal.
   * Salidas: Temporal registrado en las listas.
   *
   * @param instruccion Declaracion del temporal.
   */
  private void registrarTemporal(String instruccion) {
    String[] partes = instruccion.split("\\s+", 2);

    String tipo = partes[0].substring(5);
    String nombre = partes[1];

    nombresTemporales.add(nombre);
    tiposTemporales.add(tipo);
    registrosTemporales.add(null);
  }

  /**
   * Nombre: asignarRegistroTemporal
   * Descripcion: Asigna un registro MIPS disponible
   * a un temporal del codigo intermedio.
   * Entradas: Nombre del temporal.
   * Salidas: Registro MIPS asignado.
   *
   * @param nombre Nombre del temporal.
   * @return Registro asignado o null si no hay disponibles.
   */
  private String asignarRegistroTemporal(String nombre) {
    int posicion = nombresTemporales.indexOf(nombre);

    if (posicion == -1) {
      throw new IllegalArgumentException(
          "Temporal no registrado: " + nombre
      );
    }

    String registro = registrosTemporales.get(posicion);

    if (registro != null) {
      return registro;
    }

    String tipo = tiposTemporales.get(posicion);

    String[] disponibles = tipo.equals("float")
        ? registrosFlotantes
        : registrosEnteros;

    for (String candidato : disponibles) {
      if (!registrosTemporales.contains(candidato)) {
        registrosTemporales.set(posicion, candidato);
        return candidato;
      }
    }

    return null;
  }

  /**
   * Nombre: liberarRegistroTemporal
   * Descripcion: Libera el registro MIPS asignado a un temporal.
   * Entradas: Nombre del temporal.
   * Salidas: Registro disponible para una nueva asignacion.
   *
   * @param nombre Nombre del temporal.
   */
  private void liberarRegistroTemporal(String nombre) {
    int posicion = nombresTemporales.indexOf(nombre);

    if (posicion != -1) {
      registrosTemporales.set(posicion, null);
    }
  }

  /**
   * Nombre: tieneUsoPosterior
   * Descripcion: Busca referencias posteriores a un temporal
   * dentro de la misma funcion o seccion global.
   * Entradas: Nombre del temporal y posicion actual.
   * Salidas: Resultado de la busqueda.
   *
   * @param nombre Nombre del temporal.
   * @param posicion Posicion actual en el codigo intermedio.
   * @return true si existe una referencia posterior.
   */
  private boolean tieneUsoPosterior(String nombre, int posicion) {
    for (int i = posicion + 1; i < codigoIntermedio.size(); i++) {
      String instruccion = codigoIntermedio.get(i).trim();

      if (instruccion.startsWith("endfunc ")
          || instruccion.startsWith("func ")) {
        break;
      }

      if (instruccion.startsWith("temp_")
          || instruccion.startsWith("data_")
          || instruccion.startsWith("param_")) {
        continue;
      }

      instruccion = instruccion.replaceAll("\"[^\"]*\"|'[^']*'", "");

      String[] partes = instruccion.split("[^\\p{L}\\p{N}_]+");

      for (String parte : partes) {
        if (parte.equals(nombre)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Nombre: liberarTemporalesSinUso
   * Descripcion: Libera los registros de temporales que
   * no presentan referencias posteriores.
   * Entradas: Posicion actual del codigo intermedio.
   * Salidas: Registros disponibles para nuevas asignaciones.
   * Restricciones: Aplicable a secuencias de ejecucion lineal.
   *
   * @param posicion Posicion actual de la instruccion.
   */
  private void liberarTemporalesSinUso(int posicion) {
    for (int i = 0; i < nombresTemporales.size(); i++) {
      String nombre = nombresTemporales.get(i);
      String registro = registrosTemporales.get(i);

      if (registro != null
          && !tieneUsoPosterior(nombre, posicion)) {
        liberarRegistroTemporal(nombre);
      }
    }
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
