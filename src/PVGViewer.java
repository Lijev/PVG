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

import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;

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
    private JButton newFileButton;
    private JButton loadButton;
    private JScrollPane scrollPane;
    private JLabel coordinatesLabel; // Label для отображения координат

    public PVGViewer() {
        setTitle("PVG Viewer");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        try {
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
                updateCoordinatesLabel(); // Обновляем label координат при движении мыши
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

        loadButton = new JButton("Load");
        loadButton.addActionListener(e -> loadPvg());

        newFileButton = new JButton("New File");
        newFileButton.addActionListener(e -> createNewFile());

        JButton pipetteButton = new JButton("Pipette");
        pipetteButton.addActionListener(e-> togglePipette());

        JButton rotationButton = new JButton("Rotation Mode");
        rotationButton.addActionListener(e -> rotateMode());


        JPanel controls = new JPanel();
        controls.add(newFileButton);
        controls.add(loadButton);
        controls.add(colorButton);
        controls.add(saveButton);
        controls.add(pipetteButton);
        controls.add(rotationButton);

        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.add(layerSlider, BorderLayout.CENTER);
        sliderPanel.add(layerLabel, BorderLayout.EAST);

        coordinatesLabel = new JLabel("X: -, Y: -, Z: -"); // Инициализация label
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(coordinatesLabel, BorderLayout.WEST); // Координаты слева
        topPanel.add(controls, BorderLayout.EAST); // Кнопки справа



        scrollPane = new JScrollPane(canvas);
        add(scrollPane, BorderLayout.CENTER);
        add(sliderPanel, BorderLayout.SOUTH);
        add(topPanel,BorderLayout.NORTH); // Добавляем верхнюю панель

        updateLayerLabel();


        // Поддержка Drag and Drop
        setDropTarget(new DropTarget(this, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                java.util.List<File> droppedFiles;
                try {
                    droppedFiles = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles != null && !droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        if (file.getName().toLowerCase().endsWith(".pvg")) {
                            loadPvgFile(file);
                        }else{
                            JOptionPane.showMessageDialog(PVGViewer.this,"Error: Invalid file format. Use .pvg file.", "Error",JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(PVGViewer.this,"Error: Cannot load file...", "Error",JOptionPane.ERROR_MESSAGE);
                }
            }
        }));
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
                currentZ = y/40;
            }else if(rotationMode == 3){
                currentX = currentlayer;
                currentZ = x/40;
            }
            brushColor= new Color(voxels[currentX][currentY][currentZ], true);
            pipetteMode = false;
            canvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }
    private void createNewFile() {
        int sizeX, sizeY, sizeZ;

        try {
            String input = JOptionPane.showInputDialog(this, "Enter dimensions (X Y Z):");
            if (input == null || input.isEmpty()) return;

            String[] parts = input.split(" ");
            sizeX = Integer.parseInt(parts[0]);
            sizeY = Integer.parseInt(parts[1]);
            sizeZ = Integer.parseInt(parts[2]);


            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid dimensions. Dimensions must be positive integers.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            SizeX = sizeX;
            SizeY = sizeY;
            SizeZ = sizeZ;
            voxels = new int[SizeX][SizeY][SizeZ];
            currentlayer = sizeZ / 2;
            layerSlider.setMaximum(sizeZ - 1);
            layerSlider.setValue(currentlayer);
            updateLayerLabel();
            updateCanvasSize();
            canvas.repaint();
            updateCoordinatesLabel(); // Обновляем координаты при создании файла
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid input. Please enter integers.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void updateCanvasSize() {
        int widthDimension = getWidthDimension();
        int heightDimension = getHeightDimension();
        canvas.setPreferredSize(new Dimension(widthDimension * 40, heightDimension * 40));
        scrollPane.revalidate();
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
        updateCanvasSize();
        canvas.repaint();
        updateCoordinatesLabel();
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
                currentZ = y/40;
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
    private void updateCoordinatesLabel() {
        int voxelX = getVoxelX(lastMouseX);
        int voxelY = getVoxelY(lastMouseY);
        int currentX = voxelX;
        int currentY = voxelY;
        int currentZ = currentlayer;
        if (voxelX >= 0 && voxelX < getWidthDimension() && voxelY >= 0 && voxelY < getHeightDimension()) {
            if(rotationMode == 2){
                currentY = currentlayer;
                currentZ = lastMouseY/40;
            }else if(rotationMode == 3){
                currentX = currentlayer;
                currentZ = lastMouseX/40;
            }
            coordinatesLabel.setText("X: " + currentX + ", Y: " + currentY + ", Z: " + currentZ);
        } else {
            coordinatesLabel.setText("X: -, Y: -, Z: -");
        }
    }
    private void loadPvgFile(File file) {
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
            updateCanvasSize();
            canvas.repaint();

            JOptionPane.showMessageDialog(this,"File has been loaded!");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,"Error loading file...", "Error",JOptionPane.ERROR_MESSAGE);
        }
    }
    private void loadPvg() {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("PVG files", "pvg");
        fileChooser.setFileFilter(filter);
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            loadPvgFile(file);
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
            if (args.length > 0) {
                File file = new File(args[0]);
                if (file.exists() && file.getName().toLowerCase().endsWith(".pvg")) {
                    viewer.loadPvgFile(file);
                }else {
                    JOptionPane.showMessageDialog(viewer,"Error: Invalid file format. Use .pvg file.", "Error",JOptionPane.ERROR_MESSAGE);
                }
            }
            viewer.setVisible(true);
        });
    }
}