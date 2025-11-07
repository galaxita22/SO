module com.example.proyecto {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.proyecto to javafx.fxml;
    exports com.example.proyecto;
}