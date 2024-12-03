package org.example.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

@Path("/users")
public class UserResource {

    // Método para eliminar un usuario
    @DELETE
    @Path("/delete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteUser(@QueryParam("username") String username) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "DELETE FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
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
    }

    // Método para iniciar sesión
    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response loginUser(User user) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {
    
            String query = "SELECT password_hash FROM users WHERE username = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, user.getUsername());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        // Verifica la contraseña ingresada con la almacenada (hash)
                        if (BCrypt.checkpw(user.getPasswordHash(), storedHash)) {
                            return Response.ok("{\"message\":\"Login successful!\"}").build();
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

    // Método para registrar un nuevo usuario
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerUser(User user) {
        // Validar que el nombre de usuario y la contraseña no sean vacíos y tengan al menos 2 caracteres
        if (user.getUsername() == null || user.getUsername().length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Username must be at least 2 characters long\"}").build();
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().length() < 2) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"message\":\"Password must be at least 2 characters long\"}").build();
        }

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            // Verificar si el nombre de usuario ya existe
            String checkQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
                checkStmt.setString(1, user.getUsername());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return Response.status(Response.Status.CONFLICT).entity("{\"message\":\"Username already exists\"}").build();
                    }
                }
            }

            // Cifra la contraseña antes de guardarla en la base de datos
            String hashedPassword = BCrypt.hashpw(user.getPasswordHash(), BCrypt.gensalt());

            // Insertar el nuevo usuario en la base de datos
            String query = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, hashedPassword);
                stmt.executeUpdate();
            }

            return Response.status(Response.Status.CREATED).entity("{\"message\":\"User registered successfully!\"}").build();

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"message\":\"Database error\"}").build();
        }
    }

    // Clase interna para manejar el usuario en el cuerpo de las peticiones
    public static class User {
        private String username;
        private String passwordHash;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPasswordHash() { return passwordHash; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    }
}