# Soft Hardcore — Fabric / Minecraft Java 1.21.10

Mod base que hace que cada muerte quite permanentemente un corazón máximo. Empiezas con 10 corazones; cada muerte reduce el máximo en uno. Al agotar las vidas, los jugadores conectados pasan a espectador y el mundo queda marcado como terminado en datos persistentes.

## Requisitos
- Minecraft Java Edition **1.21.10**
- Java JDK **21**
- Fabric Loader y Fabric API para 1.21.10
- Gradle wrapper (si no se incluye, puedes abrir la carpeta con IntelliJ IDEA para generar/importar el wrapper)

## Compilar
En Windows, desde PowerShell en esta carpeta:
```powershell
gradle build
```
En Linux/macOS:
```bash
gradle build
```
El JAR remapeado se genera en `build/libs/soft-hardcore-1.0.0.jar`. Copia ese archivo a `.minecraft/mods` junto con Fabric API compatible.

## Instalar y probar
1. Instala Fabric Loader para Minecraft 1.21.10.
2. Añade Fabric API de la misma versión del juego a `.minecraft/mods`.
3. Copia `soft-hardcore-1.0.0.jar` a `.minecraft/mods`.
4. Crea un mundo de prueba y muere. Cada muerte debería reducir el máximo de vida y mostrar un aviso.
5. Al agotar las vidas, el mundo cambia a espectador para los jugadores conectados.

## Notas de esta versión inicial
- La lógica de corazones y el estado de mundo son persistentes en NBT.
- El icono roto del HUD es un recurso placeholder pixel-art. Es una primera implementación visual y puede necesitar ajuste de posición dependiendo de efectos de HUD, escala y otros mods.
- La versión inicial está planteada como base de desarrollo: compila y prueba con el entorno de Fabric 1.21.10; los cambios de mappings/API pueden requerir pequeños ajustes.
- El modo espectador impide continuar la partida, pero no elimina los archivos del mundo. Haz copias de seguridad antes de probar cualquier mod que cambie la progresión de una partida.
