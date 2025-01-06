import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
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
import java.util.Objects;
import java.awt.Cursor;
import java.net.URL;

public class PVGViewer extends JFrame {
    private int SizeX = 10;
    private int SizeY = 10;
    private int SizeZ = 10;
    private int currentlayer = SizeZ/2;
    private int[][][] voxels;
    private Color brushColor = Color.WHITE;
    private int rotationMode = 1; // 1: XY-Z, 2: XZ-Y, 3: ZY-X
    private boolean pipetteMode = false;
    private int lastMouseX = -1;
    private int lastMouseY = -1;

    private JPanel canvas;
    private JSlider layerSlider;
    private JLabel layerLabel;

    public PVGViewer() {
        setTitle("PVG Viewer");
        setSize(500, 550); // Увеличиваем высоту окна
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        try {
            // Загрузка иконки из ресурсов
            URL iconURL = getClass().getClassLoader().getResource("icon.png");
            if (iconURL != null) {
                BufferedImage icon = ImageIO.read(iconURL);
                setIconImage(icon);
            } else {
                JOptionPane.showMessageDialog(this, "Error: Icon resource not found!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading icon!", "Error", JOptionPane.ERROR_MESSAGE);
        }


        voxels = new int[SizeX][SizeY][SizeZ];

        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSlice(g);
            }
        };
        canvas.setPreferredSize(new Dimension(400, 400));
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(pipetteMode){
                    pickColor(e.getX(), e.getY());
                } else{
                    changeColor(e.getX(), e.getY());
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                if (pipetteMode){
                    canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (pipetteMode){
                    canvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });

        layerSlider = new JSlider(0, SizeZ - 1, currentlayer);
        layerSlider.addChangeListener(e -> {
            currentlayer = layerSlider.getValue();
            updateLayerLabel();
            canvas.repaint();
        });
        layerLabel = new JLabel("Layer: " + currentlayer);

        JButton colorButton = new JButton("Choose Color");
        colorButton.addActionListener(e -> chooseColor());

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> savePvg());

        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(e-> loadPvg());

        JButton pipetteButton = new JButton("Pipette");
        pipetteButton.addActionListener(e-> togglePipette());

        JButton rotationButton = new JButton("Rotation Mode");
        rotationButton.addActionListener(e -> rotateMode());


        JPanel controls = new JPanel();
        controls.add(colorButton);
        controls.add(saveButton);
        controls.add(loadButton);
        controls.add(pipetteButton);
        controls.add(rotationButton);



        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.add(layerSlider, BorderLayout.CENTER);
        sliderPanel.add(layerLabel, BorderLayout.EAST);

        add(canvas, BorderLayout.CENTER);
        add(sliderPanel, BorderLayout.SOUTH); // Добавляем панель со слайдером и текстом
        add(controls, BorderLayout.NORTH);
        updateLayerLabel(); // Устанавливаем начальное значение текста ползунка
    }
    private void togglePipette(){
        pipetteMode = !pipetteMode;
        if (pipetteMode) {
            canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            canvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }
    private void pickColor(int x, int y){
        int voxelX = getVoxelX(x);
        int voxelY = getVoxelY(y);
        if (voxelX >= 0 && voxelX < getWidthDimension() && voxelY >=0 && voxelY<getHeightDimension()) {
            int currentX = voxelX;
            int currentY = voxelY;
            int currentZ = currentlayer;
            if(rotationMode == 2){
                currentY = currentlayer;
                currentZ = y/40; // Исправлено
            }else if(rotationMode == 3){
                currentX = currentlayer;
                currentZ = x/40;
            }
            brushColor= new Color(voxels[currentX][currentY][currentZ], true);
            pipetteMode = false;
            canvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }


    private void rotateMode() {
        rotationMode = (rotationMode % 3) + 1;
        if(rotationMode == 1){
            JOptionPane.showMessageDialog(this, "Rotation Mode: XY-Z");
        }else if(rotationMode == 2){
            JOptionPane.showMessageDialog(this, "Rotation Mode: XZ-Y");
        }else{
            JOptionPane.showMessageDialog(this, "Rotation Mode: ZY-X");
        }

    }
    private void updateLayerLabel() {
        layerLabel.setText("Layer: " + currentlayer);
    }
    private void drawSlice(Graphics g) {
        int widthDimension = getWidthDimension();
        int heightDimension = getHeightDimension();
        for (int x = 0; x < widthDimension; x++) {
            for (int y = 0; y < heightDimension; y++) {
                int currentX = x;
                int currentY = y;
                int currentZ = currentlayer;
                if(rotationMode == 2){
                    currentY = currentlayer;
                    currentZ = y;
                }else if(rotationMode == 3){
                    currentX = currentlayer;
                    currentZ = x;
                }

                Color color = new Color(voxels[currentX][currentY][currentZ], true);
                g.setColor(color);
                g.fillRect(x * 40, y * 40, 40, 40);
                g.setColor(Color.BLACK);
                g.drawRect(x * 40, y * 40, 40, 40);

            }
        }
    }

    private void chooseColor() {
        brushColor = JColorChooser.showDialog(this, "Choose a color", brushColor);
    }

    private void changeColor(int x, int y) {
        int voxelX = getVoxelX(x);
        int voxelY = getVoxelY(y);
        if (voxelX >= 0 && voxelX < getWidthDimension() && voxelY >= 0 && voxelY < getHeightDimension()) {
            int currentX = voxelX;
            int currentY = voxelY;
            int currentZ = currentlayer;
            if(rotationMode == 2){
                currentY = currentlayer;
                currentZ = y/40; // Исправлено
            }else if(rotationMode == 3){
                currentX = currentlayer;
                currentZ = x/40;
            }
            voxels[currentX][currentY][currentZ] = brushColor.getRGB();
            canvas.repaint();
        }
    }
    private int getVoxelX(int x){
        int voxelX;
        if (rotationMode == 3){
            voxelX = currentlayer;
        }else{
            voxelX = x/40;
        }
        return voxelX;
    }
    private int getVoxelY(int y){
        int voxelY;
        if (rotationMode == 2){
            voxelY = currentlayer;
        }else{
            voxelY = y/40;
        }
        return voxelY;
    }
    private int getWidthDimension(){
        int widthDimension;
        if(rotationMode == 3){
            widthDimension = SizeY;
        }else{
            widthDimension = SizeX;
        }
        return widthDimension;
    }
    private int getHeightDimension(){
        int heightDimension;
        if(rotationMode == 2){
            heightDimension = SizeZ;
        }else{
            heightDimension = SizeY;
        }
        return heightDimension;
    }

    private void loadPvg() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("PVG files", "pvg");
        fileChooser.setFileFilter(filter);
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
                SizeX = buffer.getInt();
                SizeY = buffer.getInt();
                SizeZ = buffer.getInt();

                voxels = new int[SizeX][SizeY][SizeZ];
                for (int x = 0; x < SizeX; x++) {
                    for (int y = 0; y < SizeY; y++) {
                        for (int z = 0; z < SizeZ; z++) {
                            voxels[x][y][z] = buffer.getInt();
                        }
                    }
                }
                currentlayer = SizeZ/2;
                layerSlider.setMaximum(SizeZ-1);
                layerSlider.setValue(currentlayer);
                updateLayerLabel();
                canvas.repaint();

                JOptionPane.showMessageDialog(this,"File has been loaded!");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,"Error loading file...", "Error",JOptionPane.ERROR_MESSAGE);
            }
        }

    }


    private void savePvg() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("PVG files", "pvg");
        fileChooser.setFileFilter(filter);
        int returnVal = fileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".pvg")) {
                file = new File(file.getParentFile(), file.getName() + ".pvg");
            }
            try {
                ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + SizeX * SizeY * SizeZ * 4);
                buffer.putInt(SizeX);
                buffer.putInt(SizeY);
                buffer.putInt(SizeZ);
                for (int[][] layer : voxels) {
                    for (int[] row : layer) {
                        for (int color : row) {
                            buffer.putInt(color);
                        }
                    }
                }
                Files.write(file.toPath(), buffer.array());
                JOptionPane.showMessageDialog(this, "File has been saved as " + file.getName());
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error with saving file...", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PVGViewer viewer = new PVGViewer();
            viewer.setVisible(true);
        });
    }
}