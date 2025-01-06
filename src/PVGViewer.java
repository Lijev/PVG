import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class PVGViewer extends JFrame {
    private int SizeX = 10;
    private int SizeY = 10;
    private int SizeZ = 10;
    private int currentlayer = SizeZ/2;
    private int[][][] voxels;
    private Color brushColor = Color.WHITE;

    private JPanel canvas;
    private JSlider layerSlider;

    public PVGViewer() {
        setTitle("PVG Viewer");
        setSize(500,500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        voxels=new int[SizeX][SizeY][SizeZ];
        canvas = new JPanel(){
            @Override
            protected void paintComponent(Graphics g){
                super.paintComponent(g);
                drawSlice(g);
            }
        };
        canvas.setPreferredSize(new Dimension(400,400));
        canvas.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e){
                changeColor(e.getX(), e.getY() );
            }
        });

        layerSlider = new JSlider(0,SizeZ-1,currentlayer);
        layerSlider.addChangeListener(e -> {
            currentlayer=layerSlider.getValue();
            canvas.repaint();
        });

        JButton colorButton = new JButton("Choose a color");
        colorButton.addActionListener(e-> chooseColor());

        JButton saveButton = new JButton("Save as .pvg file");
        saveButton.addActionListener(e->savePvg());

        JPanel controls = new JPanel();
        controls.add(colorButton);
        controls.add(saveButton);

        add(canvas,BorderLayout.CENTER);
        add(layerSlider,BorderLayout.SOUTH);
        add(controls, BorderLayout.NORTH);
    }

    private void drawSlice(Graphics g){
        for (int x = 0; x<SizeX; x++){
            for (int y = 0; y<SizeY; y++) {
                Color color = new Color(voxels[x][y][currentlayer],true);
                g.setColor(color);
                g.fillRect(x*40,y*40,40,40);
                g.setColor(Color.BLACK);
                g.drawRect(x*40,y*40,40,40);
            }
        }
    }

    private void chooseColor(){
        brushColor = JColorChooser.showDialog(this,"Choose a color",brushColor);
    }
    private void changeColor(int x, int y){
        int voxelX = x/40;
        int voxelY = y/40;
        if (voxelX >= 0 && voxelX < SizeX && voxelY >=0 && voxelY<SizeY) {
            voxels[voxelX][voxelY][currentlayer]=brushColor.getRGB();
            canvas.repaint();
        }
    }
    private void savePvg(){
        try {
            ByteBuffer buffer = ByteBuffer.allocate(4+4+4+SizeX*SizeY*SizeZ*4);
            buffer.putInt(SizeX);
            buffer.putInt(SizeY);
            buffer.putInt(SizeZ);
            for (int[][] layer : voxels) {
                for (int[] row : layer){
                    for (int color : row){
                        buffer.putInt(color);
                    }
                }
            }
            Files.write(Paths.get("output.pvg"),buffer.array());
            JOptionPane.showMessageDialog(this,"File has been saved as output.pvg");
        } catch (IOException e){
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,"Error with saving file...");
        }
    }
    public static void main(String[] args){
        SwingUtilities.invokeLater(()->{
            PVGViewer viewer = new PVGViewer();
            viewer.setVisible(true);
        });
    }
}
