import at.favre.lib.crypto.bcrypt.BCrypt;

public class HashPassword {
    public static void main(String[] args) {
        String password = "test1234";
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        System.out.println(hash);
    }
}
