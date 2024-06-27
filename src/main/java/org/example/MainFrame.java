import org.yaml.snakeyaml.Yaml;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;

import static java.awt.RenderingHints.KEY_ANTIALIASING;

public class MainFrame extends JFrame {

    // размер окна
    final static int W = 1200;
    final static int H = 800;

    // размер сетки
    final int D = 25;

    // экземпляр игры
    Game theGame = new Game(W / 2 - 50, H - 50);
    // вспомогательный объект для эмуляции нажатий кнопки
    Robot robot;

    // координаты корабля
    double sail_x = -100;
    double sail_y = -100;

    // список строк для компонентов JList
    DefaultListModel<String> drakkarModel = new DefaultListModel<>();
    DefaultListModel<String> vikingModel = new DefaultListModel<>();
    // панель прокрутки для компонента JTextArea
    JScrollPane journalPane;
    // картинки для каждого типа деревни
    TreeMap<VillageType, BufferedImage> vilTypeToImg = new TreeMap<>();
    // картинка для драккара
    BufferedImage drakkarImg = null;

    // компонент для хранения кнопок и пр. + отрисовка
    class MainPanel extends JPanel {

        // списки, кнопки и пр.
        JList<String> drakkarListView = null;
        JList<String> vikingListView = null;
        JButton modelButton = new JButton("Моделирование набега");
        JButton nextYearButton = new JButton("Следующий год");
        JButton addViking = new JButton("+1 викинг");
        JButton loadDrakkarsFromFileButton = new JButton("Загрузить из YAML");
        JTextArea prognosArea = new JTextArea();
        JLabel label1 = new JLabel("Доступные драккары");
        JLabel label2 = new JLabel("Викинги для найма");
        JLabel infoLabel = new JLabel("");
        JCheckBox everyHour = new JCheckBox("Отчёт каждый час");
        MainFrame masterFrame = null;
        MainPanel thePanel = null;
        GamePanel thePanel = null;
        BufferedImage islandImg = null;
        BufferedImage drakkarImg = null;
        BufferedImage monkImg = null;
        public GamePanel(MainFrame mf) {


            MainPanel(MainFrame mf) {
            masterFrame = mf;
            thePanel = this;
            // для полного контроля будем управлять вручную
            this.setLayout(null);
            try {
                robot = new Robot();
            } catch (AWTException e) {
                throw new RuntimeException(e);
            }
            infoLabel.setBorder(new LineBorder(Color.BLACK, 1));
            // показываем текущую инфу
            showCurrentGameStats();

            // загружаем картинки
            vilTypeToImg.put(VillageType.FISHER_VILLAGE, loadImage("fish.png"));
            vilTypeToImg.put(VillageType.RANCH_VILLAGE, loadImage("cow.png"));
            vilTypeToImg.put(VillageType.PEASANT_VILLAGE, loadImage("grain.png"));
            vilTypeToImg.put(VillageType.CRAFTER_VILLAGE, loadImage("craft.png"));
            vilTypeToImg.put(VillageType.MONASTERY, loadImage("monastery.png"));
            vilTypeToImg.put(VillageType.TRADE_POST, loadImage("store.png"));
            drakkarImg = loadImage("ship.png");


            // список драккаров
            drakkarListView = new JList<>(drakkarModel);
            drakkarListView.setFont(new Font("Courier New", 2, 12));

            // добавляем компоненты
            this.add(drakkarListView);
            this.add(label1);
            this.add(label2);

            // блокируем кнопку перехода на след. год (пока не сходим в проход в этом году)
            nextYearButton.setEnabled(false);

            vikingListView = new JList<>(vikingModel);
            vikingListView.setFont(new Font("Courier New", 2, 12));
            // загружаем инфу из объекта Game и показываем в списках
            loadVikings();
            loadDrakkars();


            journalPane = new JScrollPane(prognosArea);
            journalPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            prognosArea.setFont(new Font("Courier New", 2, 11));
            prognosArea.setEditable(false);

            JScrollPane vikScrollPane = new JScrollPane(vikingListView);
            vikScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            // добавляем оставшиеся компоненты
            this.add(vikScrollPane);
            this.add(modelButton);
            this.add(nextYearButton);
            this.add(journalPane);
            this.add(everyHour);
            this.add(infoLabel);
            this.add(addViking);
            this.add(loadDrakkarsFromFileButton);


            // обработка клика на элементе списка викингов
            // если есть пометка [*], значит выбран в поход
            // если кликнуть по такому, выбор отменяется
            vikingListView.addListSelectionListener(new ListSelectionListener() {

                    if (e.getValueIsAdjusting()) return;
                    int k = vikingListView.getSelectedIndex();
                    if (k >= 0) {
                        var curStr = vikingModel.get(k);
                        if (curStr.startsWith("[*]")) {
                            vikingModel.set(k, curStr.substring(3));
                        } else {
                            vikingModel.set(k, "[*]" + curStr);
                        }
                    }
                }
            });

            // обработка нажатия на кнопку +1 викинг
            addViking.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    // если все комбинации использованы - невозможно
                    if (theGame.firstNames.size() * theGame.families.size() == theGame.allVikings.size()) {
                        JOptionPane.showMessageDialog(null, "Больше нет викингов!", "...", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // показываем цену и даём выбор
                    var userAnswer = JOptionPane.showConfirmDialog(null, String.format("Цена добавления викинга в список доступных = %d мер серебра. Согласны?", Game.PRICE_PER_NEW_VIKING), "...", JOptionPane.YES_NO_OPTION);
                    // если да
                    if (userAnswer == JOptionPane.OK_OPTION) {
                        // проверяем, хватает ли денег
                        if (theGame.silverPieces >= Game.PRICE_PER_NEW_VIKING) {
                            theGame.silverPieces -= Game.PRICE_PER_NEW_VIKING;
                            theGame.generateNewViking();
                            showCurrentGameStats();
                            loadVikings();
                            refresh();
                        } else {
                            JOptionPane.showMessageDialog(null, "Недостаточно денег!", "...", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            });
// обработка кнопки "Загрузить из YAML"
            loadDrakkarsFromFileButton.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    // используем JFileChooser
                    JFileChooser fc = new JFileChooser();
                    fc.setFileFilter(new FileFilter() {
                        @Override
                        public boolean accept(File f) {
                            if (f.isDirectory()) {
                                return true;
                            }
                            final String name = f.getName();
                            // фильтр - чтобы предлагал только правильные файлы
                            return name.endsWith(".yaml") || name.endsWith(".yml");
                        }

                        @Override
                        public String getDescription() {
                            return "*.yaml,*.yml";
                        }
                    });

                    // открываем диалог
                    var res = fc.showOpenDialog(thePanel);

                    // если файл был выбрат
                    if (res == JFileChooser.APPROVE_OPTION) {

                        // используем библиотеку
                        Yaml yaml = new Yaml();
                        // открываем поток на чтение файла
                        InputStream inputStream = null;
                        try {
                            inputStream = new FileInputStream(fc.getSelectedFile());
                        } catch (FileNotFoundException ex) {
                            throw new RuntimeException(ex);
                        }

                        try {
                            // загружаем объекты в мэп
                            Map<String, Object> obj = yaml.load(inputStream);
                            // готовимся создавать список новых драккаров
                            ArrayList<Drakkar> fromFile = new ArrayList<>();
                            // обходим набор
                            for (var x : obj.entrySet()) {

                                // предполагаем, что там спрятан список драккаров произвольной длины
                                ArrayList<Map<String, Integer>> data = (ArrayList<Map<String, Integer>>) x.getValue();

                                // обходим этот список
                                for (var t : data) {
                                    //      System.out.print(t);

                                    try {

                                        // пытаемся доставать параметры для очередного драккара
                                        int p = t.get("pairs");
                                        int hs = t.get("human_space");
                                        int cs = t.get("cargo_space");
                                        int ms = t.get("max_speed");

                                        // если все ок, конструируем и добавляем
                                        fromFile.add(new Drakkar(p, hs, cs, ms));


                                    } catch (NullPointerException npe) {
                                        JOptionPane.showMessageDialog(null, "Некорректные данные в YAML!", "...", JOptionPane.ERROR_MESSAGE);
                                        if (fromFile.size() > 0) {
                                            theGame.allDrakkars.clear();
                                        } else {
                                            return;
                                        }
                                    }
                                }

                                if (fromFile.size() == 0) {
                                    // если ничего не нашли, старый список не портится
                                    JOptionPane.showMessageDialog(null, "Нет данных!", "...", JOptionPane.INFORMATION_MESSAGE);
                                } else {

                                    // иначе старые убираем, новые вставляем
                                    theGame.allDrakkars.clear();
                                    theGame.allDrakkars.addAll(fromFile);

                                    loadDrakkars();

                                    refresh();
                                }

                                try {
                                    // закрываем поток
                                    inputStream.close();
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                                return;
                            }
                        } catch (ClassCastException cce) {
                            JOptionPane.showMessageDialog(null, "Bad file!!!", "...", JOptionPane.ERROR_MESSAGE);
                        }

                    }

                }
            });