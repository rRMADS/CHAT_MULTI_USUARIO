package client;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.*;
import org.kordamp.ikonli.feather.Feather;
import java.util.*;

/** Moldura integrada: sem bordas nativas brancas, com movimento e redimensionamento próprios. */
final class MolduraJanela extends StackPane {
    private final Stage janela;
    private final DeslizeJanela deslize;
    private final Region fundoLateral=new Region();
    private final HBox barra=new HBox();
    private final List<Region> bordas=new ArrayList<>();
    private double mouseX,mouseY,inicioX,inicioY,inicioLargura,inicioAltura;
    private boolean arrastando;
    private static final double ALCANCE=6;

    MolduraJanela(Stage janela,StackPane conteudo,Runnable fechar) {
        this(janela,conteudo,fechar,()->{});
    }
    MolduraJanela(Stage janela,StackPane conteudo,Runnable fechar,Runnable antesDeMinimizar) {
        this.janela=janela; janela.initStyle(StageStyle.UNDECORATED); janela.setMinWidth(0); janela.setMinHeight(0);
        deslize=new DeslizeJanela(janela,antesDeMinimizar);
        getStyleClass().add("window-shell"); setMinSize(0,0);
        fundoLateral.getStyleClass().add("window-side"); StackPane.setAlignment(fundoLateral,Pos.CENTER_RIGHT); painelDireito(0);
        Label nome=new Label("Conexo"); nome.getStyleClass().add("window-title"); nome.setMinWidth(0); nome.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(nome,Priority.ALWAYS);
        Button minimizar=controle("Minimizar",Feather.MINUS); minimizar.setOnAction(e -> deslize.minimizar());
        Button maximizar=controle("Maximizar",Feather.MAXIMIZE); maximizar.setOnAction(e -> deslize.alternarTamanho());
        deslize.maximizadaProperty().addListener((o,a,b)->{
            maximizar.setGraphic(Icones.criar(b ? Feather.MINIMIZE : Feather.MAXIMIZE,15));
            maximizar.setAccessibleText(b ? "Restaurar tamanho anterior" : "Maximizar"); maximizar.getTooltip().setText(maximizar.getAccessibleText()); requestLayout();
        });
        Button sair=controle("Fechar",Feather.X); sair.getStyleClass().add("window-close"); sair.setOnAction(e -> fechar.run());
        barra.getChildren().addAll(nome,minimizar,maximizar,sair); barra.setAlignment(Pos.CENTER_LEFT); barra.setMinSize(0,36); barra.setPrefHeight(36); barra.setMaxHeight(36); barra.getStyleClass().add("window-bar");
        StackPane topo=new StackPane(fundoLateral,barra); topo.setMinHeight(36); topo.setMaxHeight(36);
        VBox corpo=new VBox(topo,conteudo); corpo.setMinSize(0,0); conteudo.setMinSize(0,0); VBox.setVgrow(conteudo,Priority.ALWAYS); getChildren().add(corpo);
        arrastar();
        // Áreas transparentes de seis pixels: não pintam faixas nem roubam o espaço do conteúdo.
        Cursor[] cursores={Cursor.NW_RESIZE,Cursor.N_RESIZE,Cursor.NE_RESIZE,Cursor.E_RESIZE,Cursor.SE_RESIZE,Cursor.S_RESIZE,Cursor.SW_RESIZE,Cursor.W_RESIZE};
        for(int i=0;i<8;i++) {
            final int direcao=i; Region borda=new Region(); borda.setManaged(false); borda.setCursor(cursores[i]); borda.setPickOnBounds(true);
            borda.setOnMousePressed(e -> { deslize.prepararRedimensionamento(); capturar(e); e.consume(); });
            borda.setOnMouseDragged(e -> { redimensionar(direcao,e.getScreenX()-mouseX,e.getScreenY()-mouseY); e.consume(); });
            bordas.add(borda); getChildren().add(borda);
        }
        Rectangle recorte=new Rectangle(); recorte.widthProperty().bind(widthProperty()); recorte.heightProperty().bind(heightProperty()); setClip(recorte);
        deslize.animandoProperty().addListener((o,a,b)->requestLayout());
    }
    void encerrarTransicoes() { deslize.encerrar(); }
    void painelDireito(double largura) {
        fundoLateral.setMinWidth(largura); fundoLateral.setPrefWidth(largura); fundoLateral.setMaxWidth(largura); fundoLateral.setVisible(largura>0);
        barra.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("side"),largura>0);
    }
    private Button controle(String nome,Feather icone) {
        Button b=new Button("",Icones.criar(icone,15)); b.getStyleClass().add("window-control"); b.setAccessibleText(nome); b.setTooltip(new Tooltip(nome)); b.setFocusTraversable(false); return b;
    }
    private boolean controle(Node no) { for(Node n=no;n!=null && n!=barra;n=n.getParent()) if(n instanceof ButtonBase) return true; return false; }
    private void capturar(MouseEvent e) { mouseX=e.getScreenX(); mouseY=e.getScreenY(); inicioX=janela.getX(); inicioY=janela.getY(); inicioLargura=janela.getWidth(); inicioAltura=janela.getHeight(); }
    private void arrastar() {
        barra.setOnMousePressed(e -> { if(!controle((Node)e.getTarget())) { capturar(e); arrastando=false; } });
        barra.setOnMouseClicked(e -> { if(!controle((Node)e.getTarget()) && e.getClickCount()==2) deslize.alternarTamanho(); });
        barra.setOnMouseDragged(e -> {
            if(controle((Node)e.getTarget()) || !e.isPrimaryButtonDown() || !deslize.interagivel()) return;
            if(!arrastando) {
                if(Math.hypot(e.getScreenX()-mouseX,e.getScreenY()-mouseY)<3) return;
                deslize.prepararArraste(e.getScreenX(),e.getScreenY(),e.getSceneX()/Math.max(1,janela.getWidth())); capturar(e); arrastando=true;
            }
            janela.setX(inicioX+e.getScreenX()-mouseX); janela.setY(inicioY+e.getScreenY()-mouseY);
        });
    }
    private void redimensionar(int direcao,double dx,double dy) {
        boolean esquerda=direcao==0||direcao==6||direcao==7, direita=direcao==2||direcao==3||direcao==4;
        boolean cima=direcao<=2, baixo=direcao>=4&&direcao<=6;
        double largura=Math.max(1,inicioLargura+(esquerda ? -dx : direita ? dx : 0));
        double altura=Math.max(1,inicioAltura+(cima ? -dy : baixo ? dy : 0));
        if(esquerda) janela.setX(inicioX+inicioLargura-largura); if(cima) janela.setY(inicioY+inicioAltura-altura);
        janela.setWidth(largura); janela.setHeight(altura);
    }
    @Override protected void layoutChildren() {
        super.layoutChildren(); double w=getWidth(),h=getHeight(),a=Math.min(ALCANCE,Math.min(w,h)/2);
        double[][] limites={{0,0,a,a},{a,0,Math.max(0,w-2*a),a},{w-a,0,a,a},{w-a,a,a,Math.max(0,h-2*a)},
                {w-a,h-a,a,a},{a,h-a,Math.max(0,w-2*a),a},{0,h-a,a,a},{0,a,a,Math.max(0,h-2*a)}};
        for(int i=0;i<bordas.size();i++) { Region b=bordas.get(i); b.setVisible(!deslize.maximizada() && !deslize.animando()); double[] l=limites[i]; b.resizeRelocate(l[0],l[1],l[2],l[3]); }
    }
}
