package org.example.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.example.api.JwtUtils;

@Path("/users")
public class UserResource {

    private String validateToken(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(JwtUtils.getSecretKey()).build().parseClaimsJws(token).getBody().getSubject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String generateToken(String username) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + JwtUtils.getTokenExpiration());

        return Jwts.builder().setSubject(username).setIssuedAt(now).setExpiration(exp).signWith(JwtUtils.getSecretKey()).compact();
    }

    @DELETE
    @Path("/delete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(@HeaderParam("Authorization") String token, @QueryParam("username") String targetUsername) {

        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }

        if (targetUsername == null || targetUsername.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Target username is required\"}").build();
        }

        try {
            System.out.println("Token recibido para validación: [" + token + "]");
            String requestingUsername = validateToken(token);
            if (requestingUsername == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
            }

            try (Connection conn = DriverManager.getConnection(
                    "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

                String query = "SELECT es_admin FROM users WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, requestingUsername);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            boolean isAdmin = rs.getBoolean("es_admin");

                            if (!isAdmin && !requestingUsername.equals(targetUsername)) {
                                return Response.status(Response.Status.FORBIDDEN).entity("{\"message\":\"You do not have permission to delete this user\"}").build();
                            }
                        } else {
                            
                            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token user\"}").build();
                        }
                    }
                }

                String deleteQuery = "DELETE FROM users WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
                    stmt.setString(1, targetUsername);
                    int rowsAffected = stmt.executeUpdate();

                    if (rowsAffected == 0) {
                        return Response.status(Response.Status.NOT_FOUND).entity("{\"message\":\"User not found\"}").build();
                    }
                    return Response.ok("{\"message\":\"User deleted successfully!\"}").build();
                }

            } catch (SQLException e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }
    }

    

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUsers(@HeaderParam("Authorization") String token) {
        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }

        try {
            System.out.println("Token recibido para validación: [" + token + "]");
            String requestingUsername = validateToken(token);
            if (requestingUsername == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
            }

            try (Connection conn = DriverManager.getConnection(
                    "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

                String query = "SELECT es_admin FROM users WHERE username = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, requestingUsername);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            boolean isAdmin = rs.getBoolean("es_admin");

                            if (!isAdmin) {
                                return Response.status(Response.Status.FORBIDDEN).entity("{\"message\":\"You do not have permission to view this resource\"}").build();
                            }
                        } else {
                            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token user\"}").build();
                        }
                    }
                }

                String listQuery = "SELECT username, es_admin FROM users";
                try (PreparedStatement stmt = conn.prepareStatement(listQuery)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        StringBuilder jsonBuilder = new StringBuilder();
                        jsonBuilder.append("[");

                        boolean first = true;
                        while (rs.next()) {
                            if (!first) {
                                jsonBuilder.append(",");
                            }
                            first = false;

                            String username = rs.getString("username");
                            boolean esAdmin = rs.getBoolean("es_admin");

                            jsonBuilder.append(String.format("{\"username\":\"%s\",\"es_admin\":%b}", username, esAdmin));
                        }

                        jsonBuilder.append("]");
                        return Response.ok(jsonBuilder.toString()).build();
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginUser(User user) {
        try (Connection conn = DriverManager.getConnection(
            // e o usuario default
            "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "SELECT password_hash FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, user.getUsername());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        if (BCrypt.checkpw(user.getPasswordHash(), storedHash)) {
                            String token = generateToken(user.getUsername());

                            String jsonResponse = String.format("{\"message\":\"Login successful!\", \"token\":\"%s\"}", token);
                            return Response.ok(jsonResponse).build();
                        } else {
                            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid password\"}").build();
                        }
                    } else {
                        return Response.status(Response.Status.NOT_FOUND).entity("{\"message\":\"User not found\"}").build();
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
        }
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerUser(User user) {
        if (user.getUsername() == null || user.getUsername().length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Username must be at least 2 characters long\"}").build();
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Password must be at least 2 characters long\"}").build();
        }

        try (Connection conn = DriverManager.getConnection("jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, user.getUsername());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Response.status(Response.Status.CONFLICT).entity("{\"message\":\"Username already exists\"}").build();
                    }
                }
            }

            String hashedPassword = BCrypt.hashpw(user.getPasswordHash(), BCrypt.gensalt());
            String query = "INSERT INTO users (username, password_hash, es_admin) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, hashedPassword);
                stmt.setBoolean(3, false); // es_admin falso
                stmt.executeUpdate();
            }

            return Response.status(Response.Status.CREATED).entity("{\"message\":\"User registered successfully!\"}").build();

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
        }
    }
    @POST
    @Path("/registeradmin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerAdmin(@HeaderParam("Authorization") String token, User user) {
        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }

        String requestingUsername = validateToken(token);
        if (requestingUsername == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }

        if (user.getUsername() == null || user.getUsername().length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Username must be at least 2 characters long\"}").build();
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Password must be at least 2 characters long\"}").build();
        }

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, user.getUsername());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Response.status(Response.Status.CONFLICT).entity("{\"message\":\"Username already exists\"}").build();
                    }
                }
            }

            String hashedPassword = BCrypt.hashpw(user.getPasswordHash(), BCrypt.gensalt());
            String query = "INSERT INTO users (username, password_hash, es_admin) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, hashedPassword);
                stmt.setBoolean(3, user.isEsAdmin());
                stmt.executeUpdate();
            }

            return Response.status(Response.Status.CREATED).entity("{\"message\":\"Admin user registered successfully!\"}").build();

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
        }
    }
    @POST
    @Path("/loginadmin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginAdmin(User user) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "SELECT password_hash, es_admin FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, user.getUsername());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        boolean isAdmin = rs.getBoolean("es_admin");

                        if (BCrypt.checkpw(user.getPasswordHash(), storedHash) && isAdmin) {
                            String token = generateToken(user.getUsername());

                            String jsonResponse = String.format("{\"message\":\"Admin login successful!\", \"token\":\"%s\"}", token);
                            
                            return Response.ok(jsonResponse).build();
                        } else {
                            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid credentials or not an admin\"}").build();
                        }
                    } else {
                        return Response.status(Response.Status.NOT_FOUND).entity("{\"message\":\"User not found\"}").build();
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
        }
    }
    public static class User {
        private String username;
        private String passwordHash;
        private boolean esAdmin;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

        public boolean isEsAdmin() { return esAdmin; }
        public void setEsAdmin(boolean esAdmin) { this.esAdmin = esAdmin; }
    }
}