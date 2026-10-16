# Redblen

Editor 3D para Android, nativo em Java e OpenGL ES 2.0. Sem WebView, sem conta e sem dependências de UI.

## Objetivo
Modelagem poligonal de verdade com interface pensada para toque, inspirada em padrões de trabalho (não identidade visual ou código) do Blender e de modeladores mobile.

## Funcionalidades da versão 0.3.0
- Viewport 3D com modos **Órbita**, **Pan** e **Olhar livre**, pinça para zoom e pan com dois dedos, grade, eixos e pivô visível.
- Primitivas: cubo, esfera UV, cilindro, cone e plano.
- Seleção por toque; translação, rotação e escala XYZ ou individual em X, Y e Z; pivô de escala centralizado para não deslocar o modelo.
- Hierarquia de objetos, duplicação, remoção (botão Excluir na barra de objetos), propriedades e edição completa de materiais.
- Modo malha: seleção de vértices e faces, deslocamento de vértices e extrusão/inset de faces.
- Centro da câmera configurável por toque em uma superfície, seleção de objeto/vértice/face ou coordenadas X/Y/Z.
- Editor de materiais com seis presets (mate, plástico, aço, ouro, cromo e vidro simulado), cor RGB/HEX, metalicidade, rugosidade, reflexos de estúdio, emissão, opacidade e textura por imagem importada do Android.
- Projeção triplanar de texturas com ajustes de intensidade, repetição e rotação.
- Desfazer/refazer; documento local, projeto JSON próprio, arquivos `.redblen` portáteis com imagens incorporadas e importação/exportação OBJ.
- Construção reproduzível do APK debug via GitHub Actions.

## Como usar
1. **+ Objeto** para criar uma primitiva; toque em um objeto para selecionar.
2. **Mover**, **Girar**, **Escala** mudam o efeito do arrasto com um dedo. Ao escolher **Escala**, surge a linha de eixos: **XYZ / X / Y / Z**.
3. Dois dedos: pinça para zoom e arraste conjunto para navegar. Abra **Câmera** para alternar entre **Órbita**, **Pan** (deslizar o enquadramento) e **Olhar livre** (mudar a direção mantendo a posição).
4. Em **Câmera → Escolher na cena**, toque em um ponto da malha ou do chão para fixar o centro de órbita. Também é possível usar **Seleção**, digitar X/Y/Z, ou retornar à origem.
5. Em **Face** toque numa face, então **Extrudar** ou **Inset**. Em **Vértice** toque no vértice, depois arraste para mover.
6. Abra **Editar** para valores numéricos ou **Material** para personalizar aparência; o painel **Material** permite importar PNG/JPEG pelo seletor Android.
7. Abra **Arquivo** para salvar/importar/exportar.

## APK
Acesse [Actions](https://github.com/DiscNet/redblen/actions/workflows/android.yml), escolha a execução mais recente bem-sucedida e baixe o artefato **Redblen-v0.3.0-debug-apk** (contém o APK).

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

## Limites do teste
Build debug via GitHub Actions; a compilação automatizada não valida gestos em hardware real. Modelos muito grandes podem perder FPS porque a malha é enviada à GPU em cada redraw. O editor ainda não tem gizmos 3D por eixo nem camera ortográfica.

## Materiais da versão 0.3
- Os reflexos são aproximações da iluminação de estúdio com especular e Fresnel; não são reflexões da cena, ray tracing, mapas HDRI ou PBR físico completo.
- A textura usa projeção **triplanar automática**, não UV unwrap/editável. Funciona também em meshes OBJ sem UV; o arquivo OBJ exportado não inclui texturas nem arquivo MTL.
- Texturas importadas são reduzidas a no máximo 1024 px por dimensão e guardadas como PNG no armazenamento privado. Ao exportar `.redblen`, as imagens referenciadas são incorporadas em Base64 e preservadas quando o projeto for reimportado.
- A transparência é uma aproximação de duas passagens, sem ordenação espacial de múltiplos objetos translúcidos.
