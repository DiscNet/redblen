# Redblen

Editor 3D para Android, nativo em Java e OpenGL ES 2.0. Sem WebView, sem conta e sem dependências de UI.

## Objetivo
Modelagem poligonal de verdade com interface pensada para toque, inspirada em padrões de trabalho (não identidade visual ou código) do Blender e de modeladores mobile.

## Funcionalidades da versão 0.1.0
- Viewport 3D com câmera orbital, pinça para zoom e pan com dois dedos, grade e eixos.
- Primitivas: cubo, esfera UV, cilindro, cone e plano.
- Seleção por toque; translação, rotação e escala por gestos ou valores numéricos.
- Hierarquia de objetos, duplicação, remoção, propriedades e materiais de cor.
- Modo malha: seleção de vértices e faces, deslocamento de vértices e extrusão/inset de faces.
- Desfazer/refazer; documento local, projeto JSON próprio e importação/exportação OBJ.
- Construção reproduzível do APK debug via GitHub Actions.

## Como usar
1. **+ Objeto** para criar uma primitiva; toque em um objeto para selecionar.
2. **Mover**, **Girar**, **Escala** mudam o efeito do arrasto com um dedo. **Orbitar** navega a câmera.
3. Dois dedos: pinça para zoom e arraste conjunto para navegar.
4. Em **Face** toque numa face, então **Extrudar** ou **Inset**. Em **Vértice** toque no vértice, depois arraste para mover.
5. Abra **Propriedades** para editar valores numéricos; **Arquivo** para salvar/importar/exportar.

## APK
Acesse [Actions](https://github.com/DiscNet/redblen/actions/workflows/android.yml), escolha a execução mais recente bem-sucedida e baixe o artefato **Redblen-v0.1.0-debug-apk** (contém o APK).

## Compilar localmente
Requer JDK 17, Android SDK Platform 35 e Gradle 8.9:
```sh
gradle :app:assembleDebug
```
Saída: `app/build/outputs/apk/debug/app-debug.apk`.

## Escopo e limitações
Versão inicial; não é substituto integral do Blender. Ainda não possui rigging, animação, escultura, modificadores avançados, UV unwrapping, iluminação por cena, glTF/FBX ou exportação de materiais PBR. Arquivos OBJ guardam geometria, mas não projeto/câmera: use **Projeto (.redblen)** para continuar editando a cena.

## Referências conceituais
- [Blender Modeling Tools](https://docs.blender.org/manual/en/latest/modeling/meshes/tools/toolbar.html)
- [Prisma3D](https://play.google.com/store/apps/details?id=com.prisma3D.prisma3D)
- [3D Modeling App](https://play.google.com/store/apps/details?id=com.inforcegames.app3dmodelling)

**Código original**, desenvolvido para o Redblen.
