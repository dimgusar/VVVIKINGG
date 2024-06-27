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

            // обработка нажатия на кнопку "След. год"
            nextYearButton.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    // каждый викинг стареет на год
                    for (int k = 0; k < theGame.allVikings.size(); ++k) {
                        theGame.allVikings.get(k).fullYears += 1;
                    }

                    // меняем текущую дату на начало след. навигации
                    theGame.currentDate = LocalDate.of(theGame.currentDate.getYear() + 1, Month.MAY, 6);

                    // загружаем викингов и драккары (после похода драккар становится медленнее)
                    loadVikings();
                    loadDrakkars();
                    showCurrentGameStats();
                    // очищаем журнал
                    journalTextArea.setText("");
                    // разблокируем кнопку моделирование похода
                    modelButton.setEnabled(true);
                    // блокируем кнопку след. года
                    nextYearButton.setEnabled(false);
                    refresh();

                }
            });

            // обработка кнопки "Моделирование похода"
            modelButton.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {

                    modelRide();
                }
            });

            // обработка клика мышкой (выбор целей похода)
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);

                    // экранные координаты
                    int x = e.getX();
                    int y = e.getY();

                    // логические координаты
                    int cellx = x / D;
                    int celly = y / D;

                    // обходим деревни
                    for (int k = 0; k < theGame.allVillages.size(); ++k) {

                        var village = theGame.allVillages.get(k);

                        // если логические координаты правильные
                        if (cellx == village.getCellX(D) && celly == village.getCellY(D)) {


                            // для левой кнопки
                            if (e.getButton() == MouseEvent.BUTTON1) {
                                if (theGame.conquestPath.contains(village)) {
                                    addInfo("Этот пункт уже в маршруте!\n");
                                    return;
                                } else {
                                    theGame.conquestPath.add(village);
                                    addInfo(String.format("Добавлено в маршрут:\n[%s][%s]\n", village, village.getLootInfo()));
                                    repaint();
                                }
                            } else {
                                // для правой кнопки
                                if (theGame.conquestPath.contains(village)) {
                                    theGame.conquestPath.remove(village);
                                    addInfo(String.format("Извлечено из маршрута:\n[%s][%s]\n", village, village.getLootInfo()));
                                    repaint();
                                } else {
                                    addInfo("Этот пункт не в маршруте!\n");
                                    return;

                                }
                            }
                        }
                    }
                }
            });

            // устанавливаем размеры всех компонент
            label1.setBounds(W / 2 + 25, 45, 120, 30);
            label2.setBounds(W / 2 + 375, 45, 120, 30);
            drakkarListView.setBounds(W / 2 + 25, 70, 300, 250);
            vikScrollPane.setBounds(W / 2 + 340, 70, 230, 250);
            journalPane.setBounds(W / 2 + 25, 325, 545, 350);
            modelButton.setFocusPainted(false);
            modelButton.setBounds(W * 3 / 4 - 100, 715, 200, 30);
            everyHour.setBounds(W / 2 + 25, 675, 200, 28);
            infoLabel.setBounds(W / 2 + 25, 10, 240, 25);
            nextYearButton.setBounds(W / 2 + 340, 10, 125, 25);
            addViking.setBounds(W / 2 + 475, 10, 95, 25);
            loadDrakkarsFromFileButton.setBounds(W / 2 + 170, 45, 155, 22);
        }

        // что показывать для состояния игры (дата и серебро)
        public void showCurrentGameStats() {
            infoLabel.setText(String.format("Дата: %s  |  Серебро: %d", theGame.currentDate, theGame.silverPieces));
        }

        // как прогружать инфу про викингов в JList
        public void loadVikings() {

            // очистка всего
            vikingModel.clear();

            // заполяем список тествовой инфой про викингов
            for (int k = 0; k < theGame.allVikings.size(); ++k) {
                var vik = theGame.allVikings.get(k);
                vikingModel.addElement(String.format("%s, %d, %c, %d", vik.name, vik.fullYears, vik.sex, vik.totalRides));
            }
        }

        // аналогично для драккаров, только с шапкой
        public void loadDrakkars() {

            drakkarModel.clear();
            drakkarModel.addElement("Греб. пар|Чел. макс|Груз макс|Макс v км/ч");
            drakkarModel.addElement("---------|---------|---------|-----------");
            for (int k = 0; k < theGame.allDrakkars.size(); ++k) {
                var cd = theGame.allDrakkars.get(k);
                drakkarModel.addElement(String.format("   %2d    |    %2d   |   %3d   |   %2d\n", cd.N_PAIRS, cd.SPACE_FOR_MEN, cd.SPACE_FOR_GOODS, cd.MAX_SPEED));
            }

        }

        // для надёжной перерисовки
        public void refresh() {

            masterFrame.setState(ICONIFIED);
            masterFrame.setState(JFrame.NORMAL);
        }

        // моделирование набега
        public void modelRide() {