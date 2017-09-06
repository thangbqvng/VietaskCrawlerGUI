/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jxl.write.WriteException;
import postproject.Crawler;

/**
 * FXML Controller class
 *
 * @author thangbq
 */
public class MainWindowController implements Initializable {

    @FXML
    private Button btn_input;
    @FXML
    private Button btn_output;
    @FXML
    private TextField txt_input;
    @FXML
    private TextField txt_output;
    @FXML
    private Button btn_run;
    @FXML
    private Button btn_save;
    @FXML
    private CheckBox check_autosave;

    final FileChooser fileChooser = new FileChooser();
    @FXML
    private TextArea txt_process;
    @FXML
    private TableView<Crawler.IncInfo> tbl_preview;

    private boolean autoSave = false;

    /**
     * Initializes the controller class.
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        txt_process.setWrapText(true);

        txt_process.textProperty().addListener((ObservableValue<?> observable, Object oldValue, Object newValue) -> {
            txt_process.setScrollTop(Double.MAX_VALUE);
        });

        check_autosave.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            autoSave = newValue;
            btn_save.setDisable(newValue);
        });

        btn_input.setOnAction((final ActionEvent e) -> {
            File file = fileChooser.showOpenDialog(new Stage());
            if (file != null) {
                txt_input.setText(file.toString());
            }
        });

        btn_output.setOnAction((final ActionEvent e) -> {
            File file = fileChooser.showSaveDialog(new Stage());
            if (file != null) {
                txt_output.setText(file.toString());
            }
        });

        btn_run.setOnAction((final ActionEvent e) -> {
            Crawler.instance.init(txt_input.getText(), txt_output.getText(), txt_process, tbl_preview);
            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        Crawler.instance.run();
                        if (autoSave) {
                            Crawler.instance.saveFile();
                        }
                        txt_process.appendText("Finish!\n");
                    } catch (IOException | WriteException | InterruptedException ex) {
                    }
                }
            };
            thread.start();
        });

        btn_save.setOnAction((final ActionEvent e) -> {
            try {
                Crawler.instance.saveFile();
            } catch (IOException | WriteException ex) {
                
            }
        });
    }

}
