import at.favre.lib.crypto.bcrypt.BCrypt;

public class TestBcrypt {
    public static void main(String[] args) {
        String password = "KD83CnEf";
        String hash = "$2a$12$Ekp1aqoG73.ts5sM0yhSm.zhW2lMF8KILBS7xNXgkx5MjrP7V24zG";
        
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        
        System.out.println("Password: " + password);
        System.out.println("Hash: " + hash);
        System.out.println("Verified: " + result.verified);
    }
}
