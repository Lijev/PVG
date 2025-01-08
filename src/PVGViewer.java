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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class PVGViewer extends JFrame {
    private int SizeX = 10;
    private int SizeY = 10;
    private int SizeZ = 10;
    private int currentlayer = SizeZ / 2;
    private int[][][] voxels;
    private Color brushColor = Color.WHITE;
    private int rotationMode = 1; // 1: XY-Z, 2: XZ-Y, 3: ZY-X
    private boolean pipetteMode = false;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private boolean mousePressed = false;
    private double zoom = 1.0;
    private int voxelSize = 40;
    private boolean showGrid = true;

    private JPanel canvas;
    private JSlider layerSlider;
    private JLabel layerLabel;
    private JButton newFileButton;
    private JButton loadButton;
    private JScrollPane scrollPane;
    private JLabel coordinatesLabel; // Label для отображения координат
    private JSlider zoomSlider;
    private JButton hotkeysButton;
    private int viewX = 0;
    private int viewY = 0;
    private boolean isFullscreen = false;


    public PVGViewer() {
        setTitle("PVG Viewer 1.0101");
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
                if (pipetteMode) {
                    pickColor(e.getX(), e.getY());
                } else {
                    changeColor(e.getX(), e.getY());
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!pipetteMode) {
                    mousePressed = true;
                    changeColor(e.getX(), e.getY()); //закрашиваем воксель сразу при нажатии
                }

            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mousePressed = false;
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (pipetteMode) {
                    canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (pipetteMode) {
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
                if (mousePressed && !pipetteMode) {
                    changeColor(e.getX(), e.getY());//закрашиваем воксели при зажатии ЛКМ
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                updateCoordinatesLabel(); // Обновляем label координат при движении мыши
                if (mousePressed && !pipetteMode) {
                    changeColor(e.getX(), e.getY());//закрашиваем воксели при зажатии ЛКМ
                }
            }
        });

        layerSlider = new JSlider(0, SizeZ - 1, currentlayer);
        layerSlider.addChangeListener(e -> {
            currentlayer = layerSlider.getValue();
            updateLayerLabel();
            canvas.repaint();
        });
        layerLabel = new JLabel("Layer: " + currentlayer);

        zoomSlider = new JSlider(1, 100, 100);
        zoomSlider.addChangeListener(e -> {
            zoom = 1.0 / (zoomSlider.getValue() / 100.0);
            canvas.repaint();
            updateCanvasSize();
            updateCoordinatesLabel();
        });

        JButton colorButton = new JButton("Choose Color");
        colorButton.addActionListener(e -> chooseColor());

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> savePvg());

        loadButton = new JButton("Load");
        loadButton.addActionListener(e -> loadPvg());

        newFileButton = new JButton("New File");
        newFileButton.addActionListener(e -> createNewFile());

        JButton pipetteButton = new JButton("Pipette");
        pipetteButton.addActionListener(e -> togglePipette());

        JButton rotationButton = new JButton("Rotation Mode");
        rotationButton.addActionListener(e -> rotateMode());

        hotkeysButton = new JButton("HotKeys");
        hotkeysButton.addActionListener(e -> showHotkeys());

        JPanel controls = new JPanel();
        controls.add(newFileButton);
        controls.add(loadButton);
        controls.add(colorButton);
        controls.add(saveButton);
        controls.add(pipetteButton);
        controls.add(rotationButton);
        controls.add(hotkeysButton);

        JPanel sliderPanel = new JPanel(new BorderLayout());
        sliderPanel.add(layerSlider, BorderLayout.CENTER);
        sliderPanel.add(layerLabel, BorderLayout.EAST);

        JPanel zoomPanel = new JPanel(new BorderLayout());
        zoomPanel.add(new JLabel("Zoom"), BorderLayout.WEST);
        zoomPanel.add(zoomSlider, BorderLayout.CENTER);

        coordinatesLabel = new JLabel("X: -, Y: -, Z: -"); // Инициализация label
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(coordinatesLabel, BorderLayout.WEST); // Координаты слева
        topPanel.add(controls, BorderLayout.EAST); // Кнопки справа

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(sliderPanel, BorderLayout.NORTH);
        bottomPanel.add(zoomPanel, BorderLayout.SOUTH);


        scrollPane = new JScrollPane(canvas);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH); // Добавляем верхнюю панель

        updateLayerLabel();
        updateCanvasSize();
        setupKeyBindings();

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
                        } else {
                            JOptionPane.showMessageDialog(PVGViewer.this, "Error: Invalid file format. Use .pvg file.", "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(PVGViewer.this, "Error: Cannot load file...", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }));
    }

    private void setupKeyBindings() {
        InputMap inputMap = canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = canvas.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "toggleFullscreen");
        actionMap.put("toggleFullscreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleFullscreen();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), "newFile");
        actionMap.put("newFile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createNewFile();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0), "loadFile");
        actionMap.put("loadFile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadPvg();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "saveFile");
        actionMap.put("saveFile", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                savePvg();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0), "chooseColor");
        actionMap.put("chooseColor", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseColor();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotateMode");
        actionMap.put("rotateMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rotateMode();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "zoomIn");
        actionMap.put("zoomIn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int value = zoomSlider.getValue();
                if (value > 1) {
                    zoomSlider.setValue(value - 1);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "zoomOut");
        actionMap.put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int value = zoomSlider.getValue();
                if (value < 100) {
                    zoomSlider.setValue(value + 1);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "layerUp");
        actionMap.put("layerUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentlayer < SizeZ - 1) {
                    layerSlider.setValue(currentlayer + 1);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "layerDown");
        actionMap.put("layerDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentlayer > 0) {
                    layerSlider.setValue(currentlayer - 1);
                }
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "toggleGrid");
        actionMap.put("toggleGrid", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleGrid();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0), "showEasterEgg");
        actionMap.put("showEasterEgg", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showEasterEgg();
            }
        });
    }

    private void toggleGrid() {
        showGrid = !showGrid;
        canvas.repaint();
    }

    private void toggleFullscreen() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();

        if (gd.isFullScreenSupported()) {
            if (!isFullscreen) {
                setUndecorated(true);
                gd.setFullScreenWindow(this);
                isFullscreen = true;
            } else {
                gd.setFullScreenWindow(null);
                setUndecorated(false);
                isFullscreen = false;
            }
            revalidate();
            repaint();
        } else {
            JOptionPane.showMessageDialog(this, "Full screen mode not supported on this device.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void showHotkeys() {
        String hotkeysText = "F-Fullscreen\n" +
                "N-New File\n" +
                "L-Load File\n" +
                "S-Save File\n" +
                "C-Choose color\n" +
                "R-Rotation\n" +
                "+ Zoom->\n" +
                "- Zoom<-\n" +
                "D Layer->\n" +
                "A Layer<-\n" +
                "G - Toggle Grid\n" +
                "V ???";
        JOptionPane.showMessageDialog(this, hotkeysText, "Hotkeys", JOptionPane.INFORMATION_MESSAGE);
    }
    private void showEasterEgg() {
        String input = JOptionPane.showInputDialog(this, "Enter the secret text:");
        if (input != null) {
            switch (input.toLowerCase()) {
                case "hacker":
                    JOptionPane.showMessageDialog(this, "You have hacked the system!", "HACKED", JOptionPane.INFORMATION_MESSAGE);
                    break;
                case "godmode":
                    JOptionPane.showMessageDialog(this, "Now you have godmode!", "GODMODE", JOptionPane.INFORMATION_MESSAGE);
                    break;
                case "iamtired":
                    JOptionPane.showMessageDialog(this, "Okay, take a break.", "Okay", JOptionPane.INFORMATION_MESSAGE);
                    break;
                case "shalopai":
                    fillVoxels("#120B8F");
                    break;
                case "fill":
                    showFillDialog();
                    break;
                default:
                    // Очищаем ввод, не сообщая об ошибке
            }

        }
    }
    private void showFillDialog() {
        JDialog dialog = new JDialog(this, "GOD", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300,200);

        JPanel buttonsPanel = new JPanel(new GridLayout(2, 2));
        JButton buttonA = new JButton("A");
        JButton buttonB = new JButton("B");
        JButton buttonColor = new JButton("Color");
        JButton buttonDo = new JButton("Do");
        buttonsPanel.add(buttonA);
        buttonsPanel.add(buttonB);
        buttonsPanel.add(buttonColor);
        buttonsPanel.add(buttonDo);
        dialog.add(buttonsPanel, BorderLayout.CENTER);


        int[] pointA = new int[3];
        int[] pointB = new int[3];
        final Color[] fillColor = {Color.WHITE}; // fillColor теперь массив из одного элемента

        buttonA.addActionListener(e -> {
            int[] coordinates = getCoordinatesInput("Enter coordinates for A (X Y Z):");
            if (coordinates != null) {
                pointA[0] = coordinates[0];
                pointA[1] = coordinates[1];
                pointA[2] = coordinates[2];
            }
        });
        buttonB.addActionListener(e -> {
            int[] coordinates = getCoordinatesInput("Enter coordinates for B (X Y Z):");
            if (coordinates != null) {
                pointB[0] = coordinates[0];
                pointB[1] = coordinates[1];
                pointB[2] = coordinates[2];
            }
        });
        buttonColor.addActionListener(e -> {
            Color selectedColor = JColorChooser.showDialog(this, "Choose a fill color", fillColor[0]);
            if (selectedColor != null) {
                fillColor[0] = selectedColor;
            }
        });

        buttonDo.addActionListener(e -> {
            try {
                fillArea(pointA[0], pointA[1],pointA[2], pointB[0],pointB[1],pointB[2], fillColor[0]);
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "I'm sorry...", "Error", JOptionPane.ERROR_MESSAGE);
            }
            dialog.dispose();
        });
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private int[] getCoordinatesInput(String message) {
        String input = JOptionPane.showInputDialog(this, message);
        if (input == null || input.isEmpty()) return null;
        try {
            String[] parts = input.split(" ");
            if(parts.length != 3){
                JOptionPane.showMessageDialog(this, "Error Input, use 'X Y Z'");
                return null;
            }
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            return new int[]{x, y, z};
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid input. Please enter integers.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    private void fillArea(int startX, int startY, int startZ, int endX, int endY, int endZ, Color color) {
        int rgb = color.getRGB();
        int minX = Math.min(startX, endX);
        int minY = Math.min(startY, endY);
        int minZ = Math.min(startZ, endZ);
        int maxX = Math.max(startX, endX);
        int maxY = Math.max(startY, endY);
        int maxZ = Math.max(startZ, endZ);
        if(minX < 0 || minY < 0 || minZ < 0 || maxX >= SizeX || maxY >= SizeY || maxZ >= SizeZ)
            throw new IllegalStateException("Invalid start or end point");
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{minX, minY, minZ});
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];
            int z = current[2];
            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ){
                voxels[x][y][z] = rgb;
                if(x + 1 < SizeX && x + 1 <= maxX)queue.add(new int[]{x + 1, y, z});
                if(x - 1 >= 0 && x - 1 >= minX)queue.add(new int[]{x - 1, y, z});
                if(y + 1 < SizeY  && y + 1 <= maxY)queue.add(new int[]{x, y+1, z});
                if(y - 1 >= 0 && y - 1 >= minY)queue.add(new int[]{x, y-1, z});
                if(z + 1 < SizeZ && z + 1 <= maxZ)queue.add(new int[]{x, y, z+1});
                if(z - 1 >= 0 && z - 1 >= minZ)queue.add(new int[]{x, y, z-1});
            }
        }
        canvas.repaint();
    }



    private void fillVoxels(String hexColor) {
        Color color = Color.decode(hexColor);
        int rgb = color.getRGB();
        for (int x = 0; x < SizeX; x++) {
            for (int y = 0; y < SizeY; y++) {
                for (int z = 0; z < SizeZ; z++) {
                    voxels[x][y][z] = rgb;
                }
            }
        }
        canvas.repaint();
    }

    private void togglePipette() {
        pipetteMode = !pipetteMode;
        if (pipetteMode) {
            canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            canvas.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private void pickColor(int x, int y) {
        int voxelX = getVoxelX(x);
        int voxelY = getVoxelY(y);
        if (voxelX >= 0 && voxelX < getWidthDimension() && voxelY >= 0 && voxelY < getHeightDimension()) {
            int currentX = voxelX;
            int currentY = voxelY;
            int currentZ = currentlayer;
            if (rotationMode == 2) {
                currentY = currentlayer;
                currentZ = y / voxelSize;
            } else if (rotationMode == 3) {
                currentX = currentlayer;
                currentZ = x / voxelSize;
            }
            brushColor = new Color(voxels[currentX][currentY][currentZ], true);
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
            return;
        }
    }

    private void updateCanvasSize() {
        int widthDimension = getWidthDimension();
        int heightDimension = getHeightDimension();
        canvas.setPreferredSize(new Dimension((int) (widthDimension * voxelSize * zoom), (int) (heightDimension * voxelSize * zoom)));
        scrollPane.revalidate();
    }

    private void rotateMode() {
        rotationMode = (rotationMode % 3) + 1;
        if (rotationMode == 1) {
            JOptionPane.showMessageDialog(this, "Rotation Mode: XY-Z");
        } else if (rotationMode == 2) {
            JOptionPane.showMessageDialog(this, "Rotation Mode: XZ-Y");
        } else {
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
                if (rotationMode == 2) {
                    currentY = currentlayer;
                    currentZ = y;
                } else if (rotationMode == 3) {
                    currentX = currentlayer;
                    currentZ = x;
                }

                Color color = new Color(voxels[currentX][currentY][currentZ], true);
                g.setColor(color);
                g.fillRect((int) (x * voxelSize * zoom), (int) (y * voxelSize * zoom), (int) (voxelSize * zoom), (int) (voxelSize * zoom));
                if (showGrid) {
                    g.setColor(Color.BLACK);
                    g.drawRect((int) (x * voxelSize * zoom), (int) (y * voxelSize * zoom), (int) (voxelSize * zoom), (int) (voxelSize * zoom));
                }

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
            if (rotationMode == 2) {
                currentY = currentlayer;
                currentZ = y / voxelSize;
            } else if (rotationMode == 3) {
                currentX = currentlayer;
                currentZ = x / voxelSize;
            }
            voxels[currentX][currentY][currentZ] = brushColor.getRGB();
            canvas.repaint();
        }
    }

    private int getVoxelX(int x) {
        int voxelX;
        if (rotationMode == 3) {
            voxelX = currentlayer;
        } else {
            voxelX = (int) (x / (voxelSize * zoom));
        }
        return voxelX;
    }

    private int getVoxelY(int y) {
        int voxelY;
        if (rotationMode == 2) {
            voxelY = currentlayer;
        } else {
            voxelY = (int) (y / (voxelSize * zoom));
        }
        return voxelY;
    }

    private int getWidthDimension() {
        int widthDimension;
        if (rotationMode == 3) {
            widthDimension = SizeY;
        } else {
            widthDimension = SizeX;
        }
        return widthDimension;
    }

    private int getHeightDimension() {
        int heightDimension;
        if (rotationMode == 2) {
            heightDimension = SizeZ;
        } else {
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
            if (rotationMode == 2) {
                currentY = currentlayer;
                currentZ = lastMouseY / voxelSize;
            } else if (rotationMode == 3) {
                currentX = currentlayer;
                currentZ = lastMouseX / voxelSize;
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
            currentlayer = SizeZ / 2;
            layerSlider.setMaximum(SizeZ - 1);
            layerSlider.setValue(currentlayer);
            updateLayerLabel();
            updateCanvasSize();
            canvas.repaint();

            JOptionPane.showMessageDialog(this, "File has been loaded!");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading file...", "Error", JOptionPane.ERROR_MESSAGE);
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
                } else {
                    JOptionPane.showMessageDialog(viewer, "Error: Invalid file format. Use .pvg file.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
            viewer.setVisible(true);
        });
    }
}