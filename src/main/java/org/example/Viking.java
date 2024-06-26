public class Viking {

    // имя (комбинирует собственное имя и род)
    final String name;
    // сколько полных лет
    int fullYears;
    // пол
    final char sex = 'M';
    // сколько походов совершил
    int totalRides = 0;

    // конструктор
    public Viking(String fn, String ln, int age){
        name = fn + " " + ln;
        fullYears = age;
    }
}