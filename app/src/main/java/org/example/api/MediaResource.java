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


@Path("/media")
public class MediaResource {
    private static final String MEDIA_PATH = "/home/ignaciofortessoria/media";

@POST
@Path("/upload")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public Response uploadMedia(
        @FormDataParam("file") InputStream fileInputStream,
        @FormDataParam("file") FormDataContentDisposition fileMetaData,
        @FormDataParam("title") String title,
        @FormDataParam("description") String description,
        @FormDataParam("category") String category) {

    String fileName = fileMetaData.getFileName();
    File highResFile = new File(MEDIA_PATH, fileName);
    File lowResFile = new File(MEDIA_PATH, "low_" + fileName);

    try {

        try (FileOutputStream out = new FileOutputStream(highResFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        String lowResPath = lowResFile.getAbsolutePath();
        String highResPath = highResFile.getAbsolutePath();

        Process ffmpegProcess = new ProcessBuilder(
            "ffmpeg", "-i", highResPath, "-vf", "scale=-2:360", "-c:v", "libx264", "-preset", "fast", lowResPath
        ).redirectErrorStream(true).start();

        Thread outputThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ffmpegProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line); 
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        outputThread.start();

        int exitCode = ffmpegProcess.waitFor();
        outputThread.join(); // Esperar a que termine el hilo de la salida

        if (exitCode != 0) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error generating low-resolution version").build();
        }

        String highResUrl = "http://34.175.133.0:8080/media/" + fileName;
        String lowResUrl = "http://34.175.133.0:8080/media/low_" + fileName;

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "INSERT INTO media (title, description, high_res_url, low_res_url, category) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, title);
                stmt.setString(2, description);
                stmt.setString(3, highResUrl); 
                stmt.setString(4, lowResUrl);
                stmt.setString(5, category);
                stmt.executeUpdate();
            }
        }

        return Response.status(Response.Status.CREATED)
                .entity("Media uploaded and processed successfully!").build();

    } catch (Exception e) {
        e.printStackTrace();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error uploading media: " + e.getMessage()).build();
    }
}
@GET
@Path("/{fileName}")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
public Response streamMedia(@PathParam("fileName") String fileName) {
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
public Response deleteMedia(Media media) {
    try (Connection conn = DriverManager.getConnection(
            "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

        // Consulta para obtener las URLs de los archivos antes de eliminar el registro
        String selectQuery = "SELECT high_res_url, low_res_url FROM media WHERE media_id = ?";
        String deleteQuery = "DELETE FROM media WHERE media_id = ?";

        try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
            selectStmt.setInt(1, media.getMediaId());
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                String highResUrl = rs.getString("high_res_url");
                String lowResUrl = rs.getString("low_res_url");

                // Eliminar archivos físicos
                File highResFile = new File(highResUrl.replace("http://localhost/media/", MEDIA_PATH + "/"));
                File lowResFile = new File(lowResUrl.replace("http://localhost/media/", MEDIA_PATH + "/"));

                if (highResFile.exists()) {
                    highResFile.delete();
                }
                if (lowResFile.exists()) {
                    lowResFile.delete();
                }

                // Eliminar registro de la base de datos
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteQuery)) {
                    deleteStmt.setInt(1, media.getMediaId());
                    int rowsAffected = deleteStmt.executeUpdate();

                    if (rowsAffected == 0) {
                        return Response.status(Response.Status.NOT_FOUND).entity("Media not found").build();
                    }
                    return Response.ok("Media deleted successfully!").build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("Media not found").build();
            }

        }
    } catch (SQLException e) {
        e.printStackTrace();
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
    }
}


    @POST
    @Path("/add")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addMedia(Media media) {
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
    public Response getMediaByCategory(@PathParam("category") String category) {
        List<Media> mediaList = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "SELECT media_id, title, description, high_res_url, low_res_url FROM media WHERE category = ?";
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
    public Response getMediaList() {
        List<Media> mediaList = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/streaming_service", "stream_user", "your_password")) {

            String query = "SELECT media_id, title, description, high_res_url, low_res_url, category FROM media";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    Media media = new Media();
                    media.setMediaId(rs.getInt("media_id"));
                    media.setTitle(rs.getString("title"));
                    media.setDescription(rs.getString("description"));
                    media.setHighResUrl(rs.getString("high_res_url"));
                    media.setLowResUrl(rs.getString("low_res_url"));
                    media.setCategory(rs.getString("category"));
                    mediaList.add(media);
                }
            }

            return Response.ok(mediaList).build();

        } catch (SQLException e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Database error").build();
        }
    }

    public static class Media {
        private int mediaId;
        private String title;
        private String description;
        private String highResUrl;
        private String lowResUrl;
        private String category;

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
    }
}