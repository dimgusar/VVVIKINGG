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
        }

        // очистка журнала
            journalPane.requestFocusInWindow();
            journalTextArea.setText("");


        // берем индекс элемента списка драккаров
        int drakkarInd = drakkarListView.getSelectedIndex();


        // мультивыбор не принимаем
            if (drakkarListView.getSelectedValuesList().size() != 1) {
            JOptionPane.showMessageDialog(null, "Выберите ровно 1 драккар!", "...", JOptionPane.INFORMATION_MESSAGE);
            return;

        }

        // индексы 0 и 1 относятся к шапке, а не драккару
            if (drakkarInd < 2) {
            JOptionPane.showMessageDialog(null, "Выберите драккар и команду!", "...", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // берем драккар из экземпляра игры
        var curDrakkar = theGame.allDrakkars.get(drakkarInd - 2);

        // список выбранных викингов (точнее, их текстового представления, но это не важно)
        var team = new ArrayList<String>();
            for (int k = 0; k < vikingModel.size(); ++k) {
            // если в GUI напротив соотв. викинга стоит метка, значит, он в команде
            if (vikingModel.get(k).startsWith("[*]")) {

                // сначала проверяем, что данный викинг не слишком старый
                for (int j = 0; j < theGame.allVikings.size(); ++j) {
                    if (vikingModel.get(k).contains(theGame.allVikings.get(j).name)) {

                        if (theGame.allVikings.get(j).fullYears >= 55) {
                            JOptionPane.showMessageDialog(null, "Викинги 55 лет и старше не могут идти в поход!", "...", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    }
                }

                // добавляем в команду
                team.add(vikingModel.get(k));
            }
        }

        // если никого не выбрали
            if (team.size() == 0) {
            JOptionPane.showMessageDialog(null, "Выберите команду!", "...", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // если только 1 - нельзя
            if (team.size() == 1) {
            JOptionPane.showMessageDialog(null, "Нужно минимум 2 викинга, чтобы грести!", "...", JOptionPane.INFORMATION_MESSAGE);
            return;

        }

        // если больше чем места на выбранном драккаре - нельзя
            if (team.size() > curDrakkar.SPACE_FOR_MEN) {
            JOptionPane.showMessageDialog(null, "Недостаточно места, команда должна быть меньше, или драккар больше!", "...", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // если не выбраны цели набега
            if (theGame.conquestPath.size() == 0) {
            JOptionPane.showMessageDialog(null, "Выбирайте цели набега по порядку!", "...", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // приступаем к моделированию
        addInfo("-------------------МОДЕЛИРОВАНИЕ НАБЕГА-----------------\n");
        addInfo(String.format("Число викингов: %d\n", team.size()));

        // счётчик часов
        int fullHours = 0;

        // загружаем еду
            curDrakkar.addLoot(LootType.FOOD, team.size() * theGame.START_FOOD_PER_VIKING);
        // загружаем викингов
        curDrakkar.currentVikings = team.size();

        // координаты картинки корабля
        sail_x = 0;
        sail_y = 0;
        // число побед
        int numberOfVictories = 0;

        // цикл по всем переходам (включая переход домой)
            for (int targetIdx = 0; targetIdx <= theGame.conquestPath.size(); ++targetIdx) {

            // сколько прошли внутри данного плеча
            double leg_path = 0;

            // очередная цель (если домой, будет нулл)
            Village target = null;
            // длина перехода
            double target_path = 0;
            // угол
            double angle_rad = 0;

            if (targetIdx == 0) {
                // первая цель (берем угол отностиельно 0,0)
                target = theGame.conquestPath.get(targetIdx);
                target_path = Math.sqrt(Math.pow(target.lattitude, 2) + Math.pow(target.longitude, 2));
                angle_rad = Math.atan2(target.lattitude, target.longitude);
            } else if (targetIdx < theGame.conquestPath.size()) {
                // остальные цели (берем угол относительно пред. цели)
                target = theGame.conquestPath.get(targetIdx);
                var prev_target = theGame.conquestPath.get(targetIdx - 1);
                target_path = Math.sqrt(Math.pow(target.lattitude - prev_target.lattitude, 2) + Math.pow(target.longitude - prev_target.longitude, 2));
                angle_rad = Math.atan2(target.lattitude - prev_target.lattitude, target.longitude - prev_target.longitude);
            } else {
                // переход домой
                var last_target = theGame.conquestPath.get(targetIdx - 1);
                target_path = Math.sqrt(Math.pow(last_target.lattitude, 2) + Math.pow(last_target.longitude, 2));
                angle_rad = Math.atan2(-last_target.lattitude, -last_target.longitude);
            }

            // показываем длину перехода и скорость на данном плече
            addInfo(String.format("Новый переход: %d км\n", (int) target_path));
            addInfo(String.format("Скорость: %.2f км/ч\n", curDrakkar.computeSpeed()));

            // пока не сделали сколько нужно км.
            while (leg_path < target_path) {
                // +1 час
                fullHours += 1;
                // сколько км прошли за час
                double hour_dist = curDrakkar.computeSpeed();

                // увеличим пройденное плечо
                leg_path += hour_dist;

                // применим это для обновления координаты картинки корабля
                sail_x += hour_dist * Math.cos(angle_rad);
                sail_y += hour_dist * Math.sin(angle_rad);
                // в начале каждых суток - обед
                if (fullHours % 24 == 1) {
                    // time to eat

                    // сколько есть еды (в десятых долях) (может быть нулл)
                    var availFood_x10_ref = curDrakkar.lootInfo.get(LootType.FOOD);
                    // сколько есть рыбы (тоже в десятых долях) (может быть нулл)
                    var availFish_x10_ref = curDrakkar.lootInfo.get(LootType.FISH);

                    // нулл переводятся в 0
                    int availFood_x10 = availFood_x10_ref == null ? 0 : availFood_x10_ref;
                    int availFish_x10 = availFish_x10_ref == null ? 0 : availFish_x10_ref;

                    // сколько нужно десятых долей на обед (рабы считаются)
                    int need_x10 = curDrakkar.currentVikings + curDrakkar.currentSlaves;
                    // отчет в журнал
                    addInfo(String.format("День #%d\n", fullHours / 24 + 1));
                    addInfo(String.format("Запасы: еда=%d/10 рыба=%d/10\n", availFood_x10, availFish_x10));
                    // если нужно больше чем сумма еды и рыбы, то плохо
                    if (need_x10 > availFood_x10 + availFish_x10) {
                        addInfo("Не хватило еды!!! Поход будет провален!!!");
                        // очищаем драккар
                        curDrakkar.clear();
                        return;
                    }

                    // едим еду
                    int food_eaten_x10 = need_x10;
                    int fish_eaten_x10 = 0;
                    // декремент еды (можем перейти в минус)
                    availFood_x10 -= need_x10;
                    // если минус
                    if (availFood_x10 < 0) {
                        // переходим на рыбу
                        availFish_x10 += availFood_x10;
                        food_eaten_x10 += availFood_x10;
                        fish_eaten_x10 -= availFood_x10;
                        availFood_x10 = 0;
                    }
                    // отчёт про съеденное
                    addInfo("Съели: ");
                    if (food_eaten_x10 > 0) {
                        addInfo(String.format("%d/10 мер еды\n", food_eaten_x10));
                    }
                    if (fish_eaten_x10 > 0) {
                        addInfo(String.format(" %d/10 мер рыбы\n", fish_eaten_x10));
                    }

                    // вставляем обновлённое число еды и рыбы
                    curDrakkar.lootInfo.put(LootType.FOOD, availFood_x10);
                    curDrakkar.lootInfo.put(LootType.FISH, availFish_x10);
                }

                try {
                    // задерживаемся для создание эффекта анимации
                    Thread.sleep(24);

                    if (fullHours % 24 == 23) {
                        // new day
                        // каждые 24 часа - новый день
                        theGame.currentDate = theGame.currentDate.plusDays(1);
                    }

                    // если поход длится больше 60 дней
                    if (fullHours > 60 * 24) {
                        addInfo("Набег длиннее 60 дней -> поход провален!\n");
                        curDrakkar.clear();
                        return;
                    }
                    // если выбран чекбокс "Каждый час" - то добавляем ежечасный отчёт
                    if (everyHour.isSelected()) {
                        addInfo(String.format("Часов в пути: %d\n", fullHours));
                        addInfo(String.format("Пройдено км: %.2f\n", leg_path));

                    // запрашиваем перерисовку, чтобы была анимацию
                    paintImmediately(0, 0, W / 2, H);

                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }

            // завершили очердное плечо

                // если это переход к очередной цели
                if (targetIdx < theGame.conquestPath.size()) {
                    // отчёт
                    addInfo(String.format("Дошли до %s\n", theGame.conquestPath.get(targetIdx)));
                    // случайный генератор
                    Random rnd = new Random();
                    // запрашиваем вероятность успеха с учетом размера команды и уровня цели
                    var fightSuccessProb = Game.probabilityFightSuccess(team.size(), target.level);

                    // бросаем "кубик" от 0 до 1
                    var dice = rnd.nextDouble();

                    addInfo(String.format("шанс успеха = %.2f%%\n", 100 * fightSuccessProb));

                    // если кубик больше вероятности, то победа
                    if (dice <= fightSuccessProb) {

                        addInfo("Успешный бой!\n");

                        // сколько свободного места на драккаре?
                        int currentEmptySpace = curDrakkar.SPACE_FOR_GOODS;
                        for (var loot : LootType.values()) {
                            if (curDrakkar.lootInfo.containsKey(loot)) {
                                if (loot == LootType.FOOD || loot == LootType.FISH) {

                                    currentEmptySpace -= curDrakkar.lootInfo.get(loot) / 10;
                                } else {

                                    currentEmptySpace -= curDrakkar.lootInfo.get(loot);
                                }
                            }
                        }
                        addInfo(String.format("Свободно в грузовом трюме: %d\n", currentEmptySpace));

                        // обходим все типы добычи
                        for (var loot : LootType.values()) {
                            // если данная цель содержит эту добычу
                            if (target.all_loot.containsKey(loot)) {

                                // достаем количество
                                var amount = target.all_loot.get(loot);
                                if (amount == 0) {
                                    continue;
                                }
                                // если нет свободного места
                                if (currentEmptySpace == 0) {
                                    addInfo("Нет места!\n");
                                }
                                // высчитываем, сколько можно загрузить
                                int loadedAmount = amount;
                                if (amount <= currentEmptySpace) {
                                    currentEmptySpace -= amount;
                                } else {
                                    loadedAmount = currentEmptySpace;
                                    currentEmptySpace = 0;
                                }

                                // добавляем
                                curDrakkar.addLoot(loot, loadedAmount);

                                // отчёт
                                addInfo(String.format("Загрузили %d мер %s\n", loadedAmount, Game.lootTypeNames.get(loot)));
                                if (loadedAmount < amount) {
                                    // если часть не влезла - показываем это
                                    addInfo(String.format("Выбросили %d мер %s\n", amount - loadedAmount, Game.lootTypeNames.get(loot)));
                                }

                            }
                        }

                        // берем рабов
                        int slaves = target.slaves;
                        // число загруженных рабов
                        int slavesTaken = 0;
                        // если пустого места в трюме экипажа хватает
                        if (slaves <= curDrakkar.SPACE_FOR_MEN - curDrakkar.currentVikings - curDrakkar.currentSlaves) {
                            // берем всех
                            slavesTaken = slaves;
                        } else {
                            // берем столько, сколько есть места
                            slavesTaken = curDrakkar.SPACE_FOR_MEN - curDrakkar.currentVikings - curDrakkar.currentSlaves;
                        }
                        /// если никого не взяли
                        if (slavesTaken == 0) {
                            addInfo("Нет места для рабов!!!");
                        }

                        // загружаем рабов
                        curDrakkar.currentSlaves += slavesTaken;

                        // отчёт
                        if (slavesTaken > 0) {
                            addInfo(String.format("Загрузили %d рабов\n", slavesTaken));
                        }
                        if (slaves > slavesTaken) {
                            addInfo(String.format("Выбросили %d рабов\n", slaves - slavesTaken));
                        }
                        // +1 победа
                        numberOfVictories += 1;
                        addInfo("\n");
                    } else {
                        // отчёт - поражение
                        addInfo("Поражение!(((\n\n");
                    }
                } else {

                    // если добрались до дома
                    addInfo("Вернулись домой!\n");
                    if (numberOfVictories == 0) {

                        // если нет побед - плохо
                        addInfo("0 побед - поход будет провален!\n");
                        curDrakkar.clear();
                        return;
                    }

                    // отчет про добычу
                    addInfo("Содержание трюма:\n");
                    int cnt = 0;
                    int fullSilver = 0;
                    // по типам добычи
                    for (var x : LootType.values()) {

                        // если такой тип есть
                        if (curDrakkar.lootInfo.containsKey(x) && curDrakkar.lootInfo.get(x) > 0) {

                            // показываем
                            int amount = curDrakkar.lootInfo.get(x);
                            // рыбу и еду делим на 10
                            if (x == LootType.FOOD || x == LootType.FISH) {

                                addInfo(String.format("%s: %d/10\n", Game.lootTypeNames.get(x), curDrakkar.lootInfo.get(x)));

                                // считаем сколько серебра можно получить
                                fullSilver += theGame.priceSilverPerLoot.get(x) * amount / 10;
                            } else {
                                addInfo(String.format("%s: %d\n", Game.lootTypeNames.get(x), curDrakkar.lootInfo.get(x)));

                                fullSilver += theGame.priceSilverPerLoot.get(x) * amount;
                            }

                            // +1 тип
                            cnt++;
                        }
                    }
                    // если есть рабы - отчёт
                    if (curDrakkar.currentSlaves > 0) {
                        addInfo(String.format("Рабов: %d\n", curDrakkar.currentSlaves));
                    }

                    // добавляем серебро с рабов
                    fullSilver += curDrakkar.currentSlaves * theGame.PRICE_SILVER_PER_SLAVE;


                    // запрос всплывающего окна - согласиться или нет
                    var userAnswer = JOptionPane.showConfirmDialog(null, "Согласиться с моделированием?", "...", JOptionPane.YES_NO_OPTION);


                    // если согласен
                    if (userAnswer == JOptionPane.OK_OPTION) {
                        // очистка драккара
                        curDrakkar.clear();

                        if (cnt == 0) {

                            addInfo("Пусто!");
                        } else {
                            // отчёт о полученном серебре
                            addInfo(String.format("Добыча продана за %d мер серебра!\n", fullSilver));
                            addInfo(String.format("Команда получает %d мер серебра\n", fullSilver / 2));
                            addInfo(String.format("Предводитель получает %d мер серебра\n", fullSilver - fullSilver / 2));

                            // предводитель получает половину
                            theGame.silverPieces += fullSilver - fullSilver / 2;
                        }

                        // всем викингам кто был в походе снимаем отметку
                        for (int k = 0; k < vikingModel.size(); ++k) {
                            if (vikingModel.get(k).startsWith("[*]")) {

                                for (int j = 0; j < theGame.allVikings.size(); ++j) {

                                    if (vikingModel.get(k).contains(theGame.allVikings.get(j).name)) {
                                        // и добавляем +1 поход в инфо
                                        theGame.allVikings.get(j).totalRides += 1;
                                        break;
                                    }
                                }
                                vikingModel.set(k, vikingModel.get(k).substring(3));
                            }
                        }

                        // драккар износился в походе, теряет скорость (но меньше 10 км/час не будет)
                        if (curDrakkar.MAX_SPEED > 10) {
                            curDrakkar.MAX_SPEED -= 1;
                        }

                        showCurrentGameStats();
                        refresh();
                        nextYearButton.setEnabled(true);
                        modelButton.setEnabled(false);
                        theGame.conquestPath.clear();
                        // обновили инфу

                    }


                }
            }
            // для удобства моделируем нажатие на PAGE_DOWN
            // чтобы сразу видеть конец отчёта
            robot.keyPress(KeyEvent.VK_PAGE_DOWN);
            robot.keyRelease(KeyEvent.VK_PAGE_DOWN);

            sail_x = -100;
            sail_y = -100;
        }
        // отрисовка панели
        @Override
        public void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // море
            g2.setColor(Color.decode("#ADD8E6"));
            g2.fillRect(0, 0, W / 2, H);
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(0.5f));

            // сетка
            for (int k = 0; k < W / 2 / D; ++k) {
                g2.drawLine(k * D, 0, k * D, H);
            }
            for (int k = 0; k < H / 2; ++k) {
                g2.drawLine(0, k * D, W / 2, k * D);
            }
            // база
            g2.setFont(new Font("Arial Unicode MS", 1, 24));
            g2.drawString("X", 4, 22);


            g2.setColor(Color.LIGHT_GRAY);
            // обход по деревням
            for (int k = 0; k < theGame.allVillages.size(); ++k) {

                var village = theGame.allVillages.get(k);
                // логические координаты
                int cellx = village.getCellX(D);
                int celly = village.getCellY(D);
                // берем картинку (может быть и нулл)
                g2.drawImage(islandImg, cellx*D-D, celly*D-D+1, null);
                var img = vilTypeToImg.get(village.theType);
                if (img == null) {
                    // отрисовка
                    g2.fillRect(cellx * D + 1, celly * D + 1, D - 2, D - 2);
                    g2.fillRect(cellx * D + 1, celly * D + 1, D - 2, D - 2);
                } else {
                    g2.drawImage(img, cellx * D, celly * D - img.getHeight(), null);
                }

            }

            // отисовка подписей (краткая инфа про деревни)
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Courier New", Font.BOLD, 12));
            for (int k = 0; k < theGame.allVillages.size(); ++k) {
                var village = theGame.allVillages.get(k);
                int cellx = village.getCellX(D);
                int celly = village.getCellY(D);
                g2.drawString(theGame.allVillages.get(k).shortInfo(), cellx * D + 3, celly * D + D / 2 + 3);
            }
            // если есть маршрут набега
            if (theGame.conquestPath.size() > 0) {
                g2.setColor(Color.red);
                g2.setStroke(new BasicStroke(1.f));
                // рисуем каждое плечо
                g2.drawLine(D / 2, D / 2, theGame.conquestPath.getFirst().getCellX(D) * D + D / 2, theGame.conquestPath.getFirst().getCellY(D) * D + D / 2);
                for (int k = 1; k < theGame.conquestPath.size(); ++k) {
                    var x1 = theGame.conquestPath.get(k - 1).getCellX(D) * D + D / 2;
                    var y1 = theGame.conquestPath.get(k - 1).getCellY(D) * D + D / 2;
                    var x2 = theGame.conquestPath.get(k).getCellX(D) * D + D / 2;
                    var y2 = theGame.conquestPath.get(k).getCellY(D) * D + D / 2;

                    g2.drawLine(x1, y1, x2, y2);
                }
                // рисуем последнее плечо
                g2.drawLine(theGame.conquestPath.getLast().getCellX(D) * D + D / 2, theGame.conquestPath.getLast().getCellY(D) * D + D / 2, D / 2, D / 2);
            }


            if (drakkarImg != null) {
                // если есть картинка драккара
                g2.drawImage(drakkarImg, (int) sail_x - D, (int) sail_y - D, null);
            } else {
                // инача прсто иконкой
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial Unicode MS", 1, 24));
                g2.drawString("\u26F5", (int) sail_x, (int) sail_y + D);
            }
        }

        // добавить текст в журнал (JTextArea)
        void addInfo(String str) {

            journalTextArea.setText(journalTextArea.getText() + str);
        }

        // загрузка картинки (расчитано на jar)
        public BufferedImage loadImage(String fileName) {

            BufferedImage buff = null;
            try {
                var stream = getClass().getResourceAsStream(fileName);
                if (stream == null) {
                    return ImageIO.read(new File("img/"+fileName));
                }
                buff = ImageIO.read(stream);
            } catch (IOException e) {

                e.printStackTrace();
                return null;
            }
            return buff;

        }

    }

    public MainFrame() {
        try {
            // системный Look and Feel (действует на кнопки  и пр.)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }
        // размеры фрейма
        this.setPreferredSize(new Dimension(W, H));
        this.setMinimumSize(new Dimension(W, H));
        this.setMaximumSize(new Dimension(W, H));
        // размер экрана
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        this.setResizable(false);
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setTitle("Планировщик набегов");
        // добавим панель внутрь фрейма
        this.getContentPane().add(new GamePanel(this));
        // разместим фрейм посередине экрана
        this.setLocation(d.width / 2 - getWidth() / 2, d.height / 2 - getHeight() / 2);

        this.setVisible(true);
    }
}