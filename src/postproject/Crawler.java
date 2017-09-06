/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package postproject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import jxl.CellView;
import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author thangbq
 */
public class Crawler {

    private final int MAX_THREAD = 4;
    private WritableCellFormat times = new WritableCellFormat(new WritableFont(WritableFont.TIMES, 10));

    private String inputFile;
    private String outputFile;
    private int sheetNum = 0;
    private WritableSheet excelSheet;
    private WritableWorkbook workbook;
    private final int maxRow = 500000;
    private int currRow = 0;
    private List<SearchInfo> input;
    public static Crawler instance = new Crawler();
    private final String url = "http://www.vietask.com/web/Tra-danh-ba.asp";
    private ExecutorService pool;
    private TextArea txt_process;
    private TableView<Crawler.IncInfo> tbl_preview;
    ObservableList<IncInfo> items;
    private boolean saved;
    
    /**
     * @throws java.net.MalformedURLException
     * @throws jxl.write.WriteException
     * @throws java.lang.InterruptedException
     */
    public void run() throws MalformedURLException, IOException, WriteException, InterruptedException {
        txt_process.appendText("Checking input..." + "\n");
        if (inputFile == null || outputFile == null || txt_process == null || tbl_preview == null) {
            txt_process.appendText("Input file or Output file is unset!" + "\n");
            txt_process.appendText("Shutdown process." + "\n");
            return;
        }
        txt_process.appendText("Reading Input...");
        input = readInput(inputFile);
        txt_process.appendText("Input File: " + inputFile + "\n");
        //Init output file
        File file = new File(outputFile);
        txt_process.appendText("Output File: " + outputFile + "\n");
        txt_process.appendText("Startring process..." + "\n");
        WorkbookSettings wbSettings = new WorkbookSettings();
        wbSettings.setLocale(new Locale("en", "EN"));
        workbook = Workbook.createWorkbook(file, wbSettings);
        newSheet();
        ObservableList<TableColumn<IncInfo, ?>> columns = tbl_preview.getColumns();
        columns.get(0).setCellValueFactory(new PropertyValueFactory<>("row"));
        columns.get(1).setCellValueFactory(new PropertyValueFactory<>("name"));
        columns.get(2).setCellValueFactory(new PropertyValueFactory<>("addr"));
        columns.get(3).setCellValueFactory(new PropertyValueFactory<>("tel"));
        tbl_preview.setItems(items);
        for (int i = 0; i < input.size(); i++) {
            final VariableHolder parentHolder = new VariableHolder(i, input.get(i).keyWord);

            int maxpage = getPage(parentHolder.i);
            txt_process.appendText("Input: " + parentHolder.keyWord + ", Pagging: " + maxpage + "\n");
            String temp = parentHolder.keyWord;
            for (int page = 2; page <= maxpage; page++) {
                final VariableHolder variableHolder = new VariableHolder(parentHolder.i, page, temp);
                Runnable thread = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            txt_process.appendText("Getting " + variableHolder.keyWord + " page " + variableHolder.page + "\n");
                            handlePage(variableHolder.i, variableHolder.page);
                        } catch (IOException | WriteException ex) {
                            txt_process.appendText(ex.toString() + "\n");
                        }
                    }
                };
                pool.execute(thread);
            }

        }
//        
        pool.shutdown();

        pool.awaitTermination(99999, TimeUnit.DAYS);
    }

    public void init(String input, String output, TextArea process, TableView<Crawler.IncInfo> tbl_preview) {
        this.inputFile = input;
        this.outputFile = output;
        this.txt_process = process;
        this.tbl_preview = tbl_preview;
        pool = Executors.newFixedThreadPool(5);
        items = FXCollections.observableArrayList();
        saved=false;
        try {
            times.setWrap(true);
        } catch (WriteException ex) {
        }
    }

    public int saveFile() throws IOException, WriteException {
        if(!saved){
        try {
            workbook.write();
            workbook.close();
            saved=true;
            return 1;
        } catch (Exception e) {
            txt_process.appendText(e.toString());
            return -1;
        }}
        else{
            txt_process.appendText("File has already been saved!\n");
            return -1;
        }

    }

    private synchronized void addRow(IncInfo ret) throws WriteException {
        addLabel(excelSheet, 0, currRow, ret.name);
        addLabel(excelSheet, 1, currRow, ret.addr);
        addLabel(excelSheet, 2, currRow, ret.tel);
        currRow++;
        if (currRow > maxRow) {
            newSheet();
        }
    }

    private class VariableHolder {

        volatile int i;
        volatile int page;
        volatile String keyWord;

        VariableHolder(int i, int page, String keyWord) {
            this.i = i;
            this.page = page;
        }

        VariableHolder(int i, String key) {
            this.i = i;
            this.keyWord = key;
        }
    }

    private int getPage(int i) throws IOException, WriteException {
        int maxpage = 1;
        String stringData = "?";
        stringData += ("ltid=" + input.get(i).type);
        stringData += ("&ttid=" + input.get(i).Location);
        stringData += ("&nnid=" + input.get(i).job);
        stringData += ("&page=" + 1);
        stringData += ("&keyword=" + URLEncoder.encode(input.get(i).keyWord, "UTF-8"));
        //Get Connect and Send Request
        URLConnection urlConnection = new URL(url + stringData).openConnection();
//        System.out.println(urlConnection.getURL());
        urlConnection.setDoOutput(true);
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("Content-Length",
                Integer.toString(stringData.getBytes("UTF-8").length));
        urlConnection.setRequestProperty("Content-Language", "vi,en-US;q=0.8,en;q=0.6");
        urlConnection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        urlConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)");
        urlConnection.addRequestProperty("Referer", url);

        urlConnection.connect();
        OutputStream outputStream = urlConnection.getOutputStream();
        outputStream.write((stringData).getBytes("UTF-8"));
        outputStream.flush();

//                System.out.println(stringData);
        //Get Res Data
        InputStream inputStream = urlConnection.getInputStream();

        String s = IOUtils.toString(inputStream, "UTF-8");
//                System.out.println(s);
        int markedPage = s.indexOf("thông tin trong <strong>");
//                System.out.println(markedPage);
        if (markedPage > 0) {
            int endHasPage = s.indexOf("</strong> trang.", markedPage);
//                    System.out.println(endHasPage);
            String hasPage = s.substring(markedPage + "thông tin trong <strong>".length(), endHasPage);
            maxpage = Integer.parseInt(hasPage);
        }

        int markedImage = 0;

//            if(s.indexOf("images/around_chitiet2.gif", markedImage + 1) < 0){
//                break;
//            }
        while ((markedImage = s.indexOf("images/around_chitiet2.gif", markedImage + 1)) > 0) {
//                System.out.println(markedImage);
            IncInfo ret = new IncInfo();
            ret.row = currRow + 1;
            int begin = s.indexOf("<strong>", markedImage + 1);
            int end = s.indexOf("</strong>", begin + 1);
            ret.name = s.substring(begin + 8, end).replaceAll("\\s{2,}", " ").trim();
            begin = s.indexOf("Địa chỉ: ", end + 1);
            end = s.indexOf("</td>", begin + 1);
            ret.addr = s.substring(begin + "Địa chỉ: ".length(), end).replaceAll("\\s+", " ");
            begin = s.indexOf("Điện thoại: ", end + 1);
            end = s.indexOf("</td>", begin + 1);
            ret.tel = s.substring(begin + "Điện thoại: ".length(), end).replaceAll("\\s{2,}", " ").trim();
            items.add(ret);
            addRow(ret);
//            System.out.println(ret);

        }
        tbl_preview.refresh();
        return maxpage;
    }

    private void handlePage(int i, int page) throws UnsupportedEncodingException, IOException, WriteException {
        String stringData = "?";
        stringData += ("ltid=" + input.get(i).type);
        stringData += ("&ttid=" + input.get(i).Location);
        stringData += ("&nnid=" + input.get(i).job);
        stringData += ("&page=" + page);
        stringData += ("&keyword=" + URLEncoder.encode(input.get(i).keyWord, "UTF-8"));
        //Get Connect and Send Request
        URLConnection urlConnection = new URL(url + stringData).openConnection();
//        System.out.println(urlConnection.getURL());
        urlConnection.setDoOutput(true);
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("Content-Length",
                Integer.toString(stringData.getBytes("UTF-8").length));
        urlConnection.setRequestProperty("Content-Language", "vi,en-US;q=0.8,en;q=0.6");
        urlConnection.addRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        urlConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64)");
        urlConnection.addRequestProperty("Referer", url);

        urlConnection.connect();
        OutputStream outputStream = urlConnection.getOutputStream();
        outputStream.write((stringData).getBytes("UTF-8"));
        outputStream.flush();

//                System.out.println(stringData);
        //Get Res Data
        InputStream inputStream = urlConnection.getInputStream();

        String s = IOUtils.toString(inputStream, "UTF-8");
//                System.out.println(s);

        int markedImage = 0;

//            if(s.indexOf("images/around_chitiet2.gif", markedImage + 1) < 0){
//                break;
//            }
        while ((markedImage = s.indexOf("images/around_chitiet2.gif", markedImage + 1)) > 0) {
//                System.out.println(markedImage);
            IncInfo ret = new IncInfo();
            int begin = s.indexOf("<strong>", markedImage + 1);
            int end = s.indexOf("</strong>", begin + 1);
            ret.row = currRow + 1;
            ret.name = s.substring(begin + 8, end).replaceAll("\\s{2,}", " ").trim();
            begin = s.indexOf("Địa chỉ: ", end + 1);
            end = s.indexOf("</td>", begin + 1);
            ret.addr = s.substring(begin + "Địa chỉ: ".length(), end).replaceAll("\\s+", " ");
            begin = s.indexOf("Điện thoại: ", end + 1);
            end = s.indexOf("</td>", begin + 1);
            ret.tel = s.substring(begin + "Điện thoại: ".length(), end).replaceAll("\\s{2,}", " ").trim();
            items.add(ret);
            addRow(ret);
//            System.out.println(ret);
        }
        tbl_preview.refresh();
    }

    public class IncInfo {

        public int row;
        public String name;
        public String addr;
        public String tel;

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddr() {
            return addr;
        }

        public void setAddr(String addr) {
            this.addr = addr;
        }

        public String getTel() {
            return tel;
        }

        public void setTel(String tel) {
            this.tel = tel;
        }

    }

    private class SearchInfo {

        public String keyWord;
        public String type;
        public String Location;
        public String job;

        @Override
        public String toString() {
            return "{" + "keyWord=" + keyWord + ", type=" + type + ", Location=" + Location + ", job=" + job + '}';
        }

        public boolean isValid() {
            if (type.equals("")) {
                type = "1";
            }
            if (Location.equals("")) {
                Location = "0";
            }
            if (job.equals(job)) {
                job = "0";
            }

            if (!keyWord.equals("")) {
                return true;
            }
            return false;
        }
    }

    private List<SearchInfo> readInput(String file) throws IOException {
        File inputWorkbook = new File(file);
        Workbook w;
        List<SearchInfo> ret = null;
        try {

            w = Workbook.getWorkbook(inputWorkbook);
            // Get the first sheet
            Sheet sheet = w.getSheet(0);
            System.out.println(sheet.getColumns() + " " + sheet.getRows());

            ret = new ArrayList<SearchInfo>(sheet.getRows());
            for (int i = 0; i < sheet.getRows(); i++) {
                SearchInfo data = new SearchInfo();
                data.keyWord = sheet.getCell(0, i).getContents().trim();
                data.type = sheet.getCell(1, i).getContents().trim();
                data.Location = sheet.getCell(2, i).getContents().trim();
                data.job = sheet.getCell(3, i).getContents().trim();
                if (!data.isValid()) {
                    break;
                }

                txt_process.appendText(data + "\n");
//                System.out.println(data);
                ret.add(data);
            }
            
        } catch (BiffException e) {
            txt_process.appendText(e.toString() + "\n");
        } finally {
            txt_process.appendText("Read done.");
        }

        return ret;

    }

    private void addLabel(WritableSheet sheet, int column, int row, String s)
            throws WriteException, RowsExceededException {
        Label label;
        label = new Label(column, row, s, times);
        sheet.addCell(label);
    }

    private void newSheet() {
        workbook.createSheet(sheetNum + "", sheetNum);
        excelSheet = workbook.getSheet(sheetNum);
        for (int x = 0; x < 5; x++) {
            CellView cell = excelSheet.getColumnView(x);
            cell.setAutosize(true);
            excelSheet.setColumnView(x, cell);
        }
    }
}
