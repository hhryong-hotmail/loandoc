package com.loandoc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UpdatePasswords {
    public static void main(String[] args) {
        String dbUrl = "jdbc:postgresql://localhost:5432/loandoc";
        String dbUser = "postgres";
        String dbPass = "postgresql";
        
        String newPassword = "dptn1!1234"; // lowercase version
        int bcryptCost = 12;
        
        try {
            Class.forName("org.postgresql.Driver");
            
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                System.out.println("Connected to database");
                
                // Get all users
                String selectSql = "SELECT user_id FROM user_account";
                try (PreparedStatement ps = conn.prepareStatement(selectSql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    int count = 0;
                    while (rs.next()) {
                        String userId = rs.getString("user_id");
                        
                        // Generate new hash for lowercase password
                        String hashedPassword = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                            .hashToString(bcryptCost, newPassword.toCharArray());
                        
                        // Update password
                        String updateSql = "UPDATE user_account SET password = ? WHERE user_id = ?";
                        try (PreparedStatement ups = conn.prepareStatement(updateSql)) {
                            ups.setString(1, hashedPassword);
                            ups.setString(2, userId);
                            int updated = ups.executeUpdate();
                            
                            if (updated > 0) {
                                System.out.println("Updated password for user: " + userId);
                                count++;
                            }
                        }
                    }
                    
                    System.out.println("\nTotal passwords updated: " + count);
                }
            }
            
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver not found");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Database error");
            e.printStackTrace();
        }
    }
}
