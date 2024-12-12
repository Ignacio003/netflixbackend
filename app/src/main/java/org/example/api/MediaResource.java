package org.example.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
// Dependencias JWT
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import org.example.api.JwtUtils;


@Path("/media")
public class MediaResource {
            private void cleanup(File... files) {
            for (File file : files) {
                if (file.exists()) {
                    if (file.isDirectory()) {
                        for (File child : file.listFiles()) {
                            child.delete();
                        }
                    }
                    file.delete();
                }
            }
        }
        private String validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(JwtUtils.getSecretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
            }
        }
        private void printProcessOutput(Process process, String label) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(label + ": " + line); 
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    private static final String MEDIA_PATH = "/home/ignaciofortessoria/media";
@POST
@Path("/upload")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public Response uploadMedia(
        @HeaderParam("Authorization") String token,
        @FormDataParam("file") InputStream fileInputStream,
        @FormDataParam("file") FormDataContentDisposition fileMetaData,
        @FormDataParam("title") String title,
        @FormDataParam("description") String description,
        @FormDataParam("category") String category) {

    System.out.println("Starting media upload...");

    if (token == null || token.isEmpty()) {
        System.out.println("Token is missing.");
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
    }

    System.out.println("Validating token...");
    String requestingUsername = validateToken(token);
    if (requestingUsername == null) {
        System.out.println("Invalid token.");
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
    }

    String fileName = fileMetaData.getFileName();
    System.out.println("Received file: " + fileName);

    File uploadedFile = new File(MEDIA_PATH, fileName);
    File hlsDir1080p = new File(MEDIA_PATH, fileName.replace(".mp4", "_hls_1080p"));
    File hlsDir360p = new File(MEDIA_PATH, fileName.replace(".mp4", "_hls_360p"));
    File mp4File360p = new File(MEDIA_PATH, fileName.replace(".mp4", "_360p.mp4"));

    try {
        System.out.println("Saving uploaded file...");
        try (FileOutputStream out = new FileOutputStream(uploadedFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("File saved successfully: " + uploadedFile.getAbsolutePath());

        if (!hlsDir1080p.exists()) {
            hlsDir1080p.mkdir();
            System.out.println("Created directory for 1080p HLS: " + hlsDir1080p.getAbsolutePath());
        }
        if (!hlsDir360p.exists()) {
            hlsDir360p.mkdir();
            System.out.println("Created directory for 360p HLS: " + hlsDir360p.getAbsolutePath());
        }

        System.out.println("Starting ffmpeg processes...");

        Process ffmpeg1080p = new ProcessBuilder(
                "ffmpeg", "-i", uploadedFile.getAbsolutePath(),
                "-vf", "scale=-2:1080",
                "-start_number", "0",
                "-preset", "veryfast",
                "-threads", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-f", "hls",
                new File(hlsDir1080p, "playlist.m3u8").getAbsolutePath()
        ).redirectErrorStream(true).start();
        System.out.println("1080p HLS process started.");

        Process ffmpeg360p = new ProcessBuilder(
                "ffmpeg", "-i", uploadedFile.getAbsolutePath(),
                "-vf", "scale=-2:360",
                "-start_number", "0",
                "-hls_time", "10",
                "-preset", "veryfast",
                "-threads", "0",
                "-hls_list_size", "0",
                "-f", "hls",
                new File(hlsDir360p, "playlist.m3u8").getAbsolutePath()
        ).redirectErrorStream(true).start();
        System.out.println("360p HLS process started.");

        Process ffmpegMp4360p = new ProcessBuilder(
                "ffmpeg", "-i", uploadedFile.getAbsolutePath(),
                "-vf", "scale=-2:360", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-c:a", "aac", "-strict", "experimental",
                mp4File360p.getAbsolutePath()
        ).redirectErrorStream(true).start();
        System.out.println("MP4 360p process started.");

        int exitCode1080p = ffmpeg1080p.waitFor();
        System.out.println("1080p HLS process completed with exit code: " + exitCode1080p);

        int exitCode360p = ffmpeg360p.waitFor();
        System.out.println("360p HLS process completed with exit code: " + exitCode360p);

        int exitCodeMp4360p = ffmpegMp4360p.waitFor();
        System.out.println("MP4 360p process completed with exit code: " + exitCodeMp4360p);

        if (exitCode1080p != 0 || exitCode360p != 0 || exitCodeMp4360p != 0) {
            System.out.println("Error in ffmpeg processes, cleaning up...");
            cleanup(uploadedFile, hlsDir1080p, hlsDir360p, mp4File360p);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error generando las versiones HLS o MP4 360p").build();
        }

        String hlsUrl1080p = "http://34.175.133.0:8080/media/" + hlsDir1080p.getName() + "/playlist.m3u8";
        String hlsUrl360p = "http://34.175.133.0:8080/media/" + hlsDir360p.getName() + "/playlist.m3u8";
        String mp4Url360p = "http://34.175.133.0:8080/media/" + mp4File360p.getName();
        String downloadUrl = "http://34.175.133.0:8080/media/" + fileName;

        System.out.println("Connecting to database...");
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "INSERT INTO media (title, description, high_res_url, low_res_url, category, hls_url_1080p, hls_url_360p) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, title);
                stmt.setString(2, description);
                stmt.setString(3, downloadUrl);
                stmt.setString(4, mp4Url360p);
                stmt.setString(5, category);
                stmt.setString(6, hlsUrl1080p);
                stmt.setString(7, hlsUrl360p);
                stmt.executeUpdate();
                System.out.println("Media data inserted into database.");
            }
        }

        System.out.println("Media uploaded and processed successfully.");
        return Response.status(Response.Status.CREATED)
                .entity("Media uploaded and processed successfully!").build();

    } catch (Exception e) {
        System.out.println("Error: " + e.getMessage());
        e.printStackTrace();
        cleanup(uploadedFile, hlsDir1080p, hlsDir360p, mp4File360p);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error uploading media: " + e.getMessage()).build();
    }
}

@GET
@Path("/{subPath: .*}")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
public Response serveFile(@HeaderParam("Authorization") String token, @PathParam("subPath") String subPath) {
    if (token == null || token.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
    }

    String requestingUsername = validateToken(token);
    if (requestingUsername == null) {
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
    }

    File file = new File(MEDIA_PATH, subPath);
    if (!file.exists() || file.isDirectory()) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("File not found or path is a directory").build();
    }

    return Response.ok((StreamingOutput) output -> {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }).header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
    .build();
}

@GET
@Path("/{fileName}")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
public Response streamMedia(@HeaderParam("Authorization") String token, @PathParam("fileName") String fileName) {
    if (token == null || token.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
    }

    String requestingUsername = validateToken(token);
    if (requestingUsername == null) {
        return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
    }

    File file = new File(MEDIA_PATH, fileName);
    if (!file.exists()) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity("File not found").build();
    }

    return Response.ok((StreamingOutput) output -> {
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }).header("Content-Disposition", "inline; filename=\"" + file.getName() + "\"")
    .build();
}
    @DELETE
    @Path("/delete")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteMedia(@HeaderParam("Authorization") String token, @QueryParam("mediaid") int mediaid) {
        
        // Validar que el token no sea nulo o vacío
        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }
    System.out.println("Token recibido: " + token);
        try {
            // Validar el token y extraer el username del solicitante
            String requestingUsername = validateToken(token);
            if (requestingUsername == null) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
            }

            // Opcional: Verificar permisos en base al username (si aplica lógica adicional)

            try (Connection conn = DriverManager.getConnection(
                    "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

                String selectQuery = "SELECT high_res_url, low_res_url, hls_url_1080p, hls_url_360p FROM media WHERE media_id = ?";
                String deleteQuery = "DELETE FROM media WHERE media_id = ?";

                try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
                    selectStmt.setInt(1, mediaid);
                    ResultSet rs = selectStmt.executeQuery();

                    if (rs.next()) {
                        String highResUrl = rs.getString("high_res_url");
                        String lowResUrl = rs.getString("low_res_url");
                        String hlsUrl1080p = rs.getString("hls_url_1080p");
                        String hlsUrl360p = rs.getString("hls_url_360p");

                        File highResFile = new File(highResUrl.replace("http://34.175.133.0:8080/media/", MEDIA_PATH + "/"));
                        File lowResFile = new File(lowResUrl.replace("http://34.175.133.0:8080/media/", MEDIA_PATH + "/"));
                        File hlsDir1080p = new File(hlsUrl1080p.replace("http://34.175.133.0:8080/media/", MEDIA_PATH + "/"));
                        File hlsDir360p = new File(hlsUrl360p.replace("http://34.175.133.0:8080/media/", MEDIA_PATH + "/"));

                        deleteFileOrDirectory(highResFile);
                        deleteFileOrDirectory(lowResFile);
                        deleteFileOrDirectory(hlsDir1080p);
                        deleteFileOrDirectory(hlsDir360p);

                        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                            deleteStmt.setInt(1, mediaid);
                            int rowsAffected = deleteStmt.executeUpdate();

                            if (rowsAffected == 0) {
                                return Response.status(Response.Status.NOT_FOUND).entity("{\"message\":\"Media not found\"}").build();
                            }
                            return Response.ok("{\"message\":\"Media deleted successfully!\"}").type(MediaType.APPLICATION_JSON).build();
                        }
                    } else {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity("{\"message\":\"Media not found\"}")
                                .type(MediaType.APPLICATION_JSON)
                                .build();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }
    }


    private void deleteFileOrDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteFileOrDirectory(child);
            }
        }
        fileOrDirectory.delete();
    }

    @POST
    @Path("/add")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addMedia(@HeaderParam("Authorization") String token, Media media) {
        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }

        String requestingUsername = validateToken(token);
        if (requestingUsername == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {
            String query = "INSERT INTO media (title, description, high_res_url, low_res_url, category) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, media.getTitle());
                stmt.setString(2, media.getDescription());
                stmt.setString(3, media.getHighResUrl());
                stmt.setString(4, media.getLowResUrl());
                stmt.setString(5, media.getCategory());
                stmt.executeUpdate();
            }

            return Response.status(Response.Status.CREATED).entity("Media added successfully!").build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
        }
    }

   @GET
    @Path("/category/{category}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMediaByCategory(@HeaderParam("Authorization") String token, @PathParam("category") String category) {
        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }

        String requestingUsername = validateToken(token);
        if (requestingUsername == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }

        List<Media> mediaList = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "SELECT media_id, title, description, high_res_url, low_res_url, hls_url_1080p, hls_url_360p FROM media WHERE category = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, category);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Media media = new Media();
                    media.setMediaId(rs.getInt("media_id"));
                    media.setTitle(rs.getString("title"));
                    media.setDescription(rs.getString("description"));
                    media.setHighResUrl(rs.getString("high_res_url"));
                    media.setLowResUrl(rs.getString("low_res_url"));
                    media.setHlsUrl1080p(rs.getString("hls_url_1080p"));
                    media.setHlsUrl360p(rs.getString("hls_url_360p"));
                    mediaList.add(media);
                }
            }

            return Response.ok(mediaList).build();

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
        }
    }

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listMedia(@HeaderParam("Authorization") String token) {
        if (token == null || token.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Token is required\"}").build();
        }

        String requestingUsername = validateToken(token);
        if (requestingUsername == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("{\"message\":\"Invalid token\"}").build();
        }

        List<Media> mediaList = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "SELECT media_id, title, description, high_res_url, low_res_url, category, hls_url_1080p, hls_url_360p FROM media";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Media media = new Media();
                    media.setMediaId(rs.getInt("media_id"));
                    media.setTitle(rs.getString("title"));
                    media.setDescription(rs.getString("description"));
                    media.setHighResUrl(rs.getString("high_res_url"));
                    media.setLowResUrl(rs.getString("low_res_url"));
                    media.setCategory(rs.getString("category"));
                    media.setHlsUrl1080p(rs.getString("hls_url_1080p"));
                    media.setHlsUrl360p(rs.getString("hls_url_360p"));
                    mediaList.add(media);
                }
            }
            return Response.ok(mediaList).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
        }
    }


    public class Media {
        private int mediaId;
        private String title;
        private String description;
        private String highResUrl;
        private String lowResUrl;
        private String category;
        private String hlsUrl1080p;
        private String hlsUrl360p;

        public int getMediaId() {
            return mediaId;
        }

        public void setMediaId(int mediaId) {
            this.mediaId = mediaId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getHighResUrl() {
            return highResUrl;
        }

        public void setHighResUrl(String highResUrl) {
            this.highResUrl = highResUrl;
        }

        public String getLowResUrl() {
            return lowResUrl;
        }

        public void setLowResUrl(String lowResUrl) {
            this.lowResUrl = lowResUrl;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getHlsUrl1080p() {
            return hlsUrl1080p;
        }

        public void setHlsUrl1080p(String hlsUrl1080p) {
            this.hlsUrl1080p = hlsUrl1080p;
        }

        public String getHlsUrl360p() {
            return hlsUrl360p;
        }

        public void setHlsUrl360p(String hlsUrl360p) {
            this.hlsUrl360p = hlsUrl360p;
        }
    }
}