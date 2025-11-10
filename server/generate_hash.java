import at.favre.lib.crypto.bcrypt.BCrypt;

public class generate_hash {
    public static void main(String[] args) {
        String password = "dPtn1!1234";
        int cost = 12;
        String hash = BCrypt.withDefaults().hashToString(cost, password.toCharArray());
        System.out.println("Password: " + password);
        System.out.println("BCrypt hash: " + hash);
        System.out.println();
        System.out.println("SQL Update Statement:");
        System.out.println("UPDATE user_account SET password = '" + hash + "' WHERE user_id = 'user_id_37';");
    }
}
