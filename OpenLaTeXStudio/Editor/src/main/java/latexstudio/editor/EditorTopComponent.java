/*                    
 * Copyright (c) 2015 Sebastian Brudzinski
 * 
 * See the file LICENSE for copying permission.
 */
package latexstudio.editor;

import com.dropbox.core.DbxAccountInfo;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import javax.swing.JOptionPane;
import javax.swing.text.BadLocationException;
import latexstudio.editor.files.FileService;
import latexstudio.editor.remote.Cloud;
import latexstudio.editor.remote.DbxState;
import latexstudio.editor.remote.DbxUtil;
import latexstudio.editor.settings.ApplicationSettings;
import latexstudio.editor.settings.SettingListener;
import latexstudio.editor.util.ApplicationUtils;
import org.apache.commons.io.IOUtils;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Top component which displays the editor window.
 */
@ConvertAsProperties(
        dtd = "-//latexstudio.editor//Editor//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "EditorTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = true)
@ActionID(category = "Window", id = "latexstudio.editor.EditorTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_EditorAction",
        preferredID = "EditorTopComponent"
)
@Messages({
    "CTL_EditorAction=Editor",
    "CTL_EditorTopComponent=Editor Window",
    "HINT_EditorTopComponent=This is a Editor window"
})
public final class EditorTopComponent extends TopComponent {

    private boolean dirty = false;
    private boolean modified = false;
    private boolean previewDisplayed = true;
    private File currentFile;
    private DbxState dbxState;

    private AutoCompletion autoCompletion = null;
    private static final ApplicationLogger LOGGER = new ApplicationLogger("Cloud Support");

    public EditorTopComponent() {
        initComponents();
        setName(Bundle.CTL_EditorTopComponent());
        setDisplayName("welcome.tex");
        setToolTipText(Bundle.HINT_EditorTopComponent());
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_UNDOCKING_DISABLED, Boolean.TRUE);

        displayCloudStatus();
    }

    @SettingListener(setting = ApplicationSettings.Setting.AUTOCOMPLETE_ENABLED)
    public void setAutocompleteEnabled(boolean value) {
        if (autoCompletion != null) {
            autoCompletion.setAutoActivationEnabled(value);
        }
    }

    @SettingListener(setting = ApplicationSettings.Setting.AUTOCOMPLETE_DELAY)
    public void setAutocompleteDelay(int value) {
        if (autoCompletion != null) {
            autoCompletion.setAutoActivationDelay(value);
        }
    }

    @SettingListener(setting = ApplicationSettings.Setting.LINEWRAP_ENABLED)
    public void setLinewrapEnabled(boolean value) {
        if (rSyntaxTextArea != null) {
            rSyntaxTextArea.setLineWrap(value);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        rSyntaxTextArea = new org.fife.ui.rsyntaxtextarea.RSyntaxTextArea();
        rTextScrollPane1 = new org.fife.ui.rtextarea.RTextScrollPane(rSyntaxTextArea);

        rSyntaxTextArea.setColumns(20);
        rSyntaxTextArea.setRows(5);
        rSyntaxTextArea.setSyntaxEditingStyle(org.openide.util.NbBundle.getMessage(EditorTopComponent.class, "EditorTopComponent.rSyntaxTextArea.syntaxEditingStyle")); // NOI18N
        rSyntaxTextArea.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                rSyntaxTextAreaKeyReleased(evt);
            }

            public void keyTyped(java.awt.event.KeyEvent evt) {
                rSyntaxTextAreaKeyTyped(evt);
            }
        });

        rTextScrollPane1.setFoldIndicatorEnabled(true);
        rTextScrollPane1.setLineNumbersEnabled(true);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(rTextScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 709, Short.MAX_VALUE)
                        .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(rTextScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 546, Short.MAX_VALUE)
                        .addContainerGap())
        );
    }// </editor-fold>                        

    private void rSyntaxTextAreaKeyReleased(java.awt.event.KeyEvent evt) {
        dirty = true;
        setModified(true);
    }

    private void rSyntaxTextAreaKeyTyped(java.awt.event.KeyEvent evt) {
        if (currentFile == null || evt.isControlDown()) {
            return;
        }
        setDisplayName(currentFile.getName() + '*');
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.fife.ui.rsyntaxtextarea.RSyntaxTextArea rSyntaxTextArea;
    private org.fife.ui.rtextarea.RTextScrollPane rTextScrollPane1;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        ApplicationUtils.deleteTempFiles();
        CompletionProvider provider = createCompletionProvider();
        autoCompletion = new AutoCompletion(provider);
        autoCompletion.install(rSyntaxTextArea);

        ApplicationSettings.INSTANCE.registerSettingListeners(this);

        String initFileDir = (String) ApplicationSettings.Setting.USER_LASTFILE.getValue();
        File initFile = new File(initFileDir);
        if (initFile.exists() && initFile.isFile()) {
            String content = FileService.readFromFile(initFileDir);
            setEditorContent(content);
            setCurrentFile(initFile);
        } else {
            InputStream is = null;
            try {
                is = getClass().getResource("/openlatexstudio/welcome.tex").openStream();
                String welcomeMessage = IOUtils.toString(is);
                setEditorContent(welcomeMessage);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
    }

    @Override
    public void componentClosed() {
    }

    public String getEditorContent() {
        return rSyntaxTextArea.getText();
    }

    public void setEditorContent(String text) {
        rSyntaxTextArea.setText(text);
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public boolean isPreviewDisplayed() {
        return previewDisplayed;
    }

    public void setPreviewDisplayed(boolean previewDisplayed) {
        this.previewDisplayed = previewDisplayed;
    }

    public void undoAction() {
        rSyntaxTextArea.undoLastAction();
    }

    public void redoAction() {
        rSyntaxTextArea.redoLastAction();
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(File currentFile) {
        this.currentFile = currentFile;

        if (currentFile != null) {
            setDisplayName(currentFile.getName());
            ApplicationSettings.Setting.USER_LASTFILE.setValue(currentFile.getAbsolutePath());
            ApplicationSettings.INSTANCE.save();
        }
    }

    public DbxState getDbxState() {
        return dbxState;
    }

    public void setDbxState(DbxState dbxState) {
        this.dbxState = dbxState;
    }

    private String findStartSymbol() {
        int carretCoordinates;
        while (true) {
            carretCoordinates = rSyntaxTextArea.getSelectionStart();
            if (rSyntaxTextArea.getSelectedText().startsWith("\n") || rSyntaxTextArea.getSelectionStart() == 0) {
                if (rSyntaxTextArea.getSelectionStart() != 0) {
                    rSyntaxTextArea.select(carretCoordinates + 1, rSyntaxTextArea.getSelectionEnd());
                } else {
                    rSyntaxTextArea.select(carretCoordinates, rSyntaxTextArea.getSelectionEnd());
                }
                return rSyntaxTextArea.getSelectedText();
            } else {
                carretCoordinates--;
                rSyntaxTextArea.select(carretCoordinates, rSyntaxTextArea.getSelectionEnd());
            }
        }
    }

    public void commentOutText() {
        String highlightedTextArea = rSyntaxTextArea.getSelectedText();

        if (highlightedTextArea != null) { // Some text is highlighted case
            highlightedTextArea = findStartSymbol();

            if (highlightedTextArea.startsWith("%")) {
                rSyntaxTextArea.replaceSelection(highlightedTextArea.replace("%", ""));
            } else {
                String[] array = highlightedTextArea.split("\n");
                StringBuilder commentedCodeBuilder = new StringBuilder();
                for (int i = 0; i < array.length; i++) {
                    array[i] = (array[i].charAt(0) == '%') ? array[i] : "%" + array[i];
                    if (i != array.length - 1) {
                        array[i] = array[i] + "\n";
                    }
                    commentedCodeBuilder.append(array[i]);
                }
                rSyntaxTextArea.replaceSelection(commentedCodeBuilder.toString());
            }
        } else {  // Nothing is highlighted case
            try {
                int currentOffsetFromLineStart = rSyntaxTextArea.getCaretOffsetFromLineStart();
                int currentCaretPosition = rSyntaxTextArea.getCaretPosition();
                int lineStartPosition = currentCaretPosition - currentOffsetFromLineStart;
                int lineLength = rSyntaxTextArea.getLineEndOffsetOfCurrentLine();

                String firstChar = rSyntaxTextArea.getText(lineStartPosition, lineLength - lineStartPosition);
                if (firstChar.startsWith("%")) {
                    rSyntaxTextArea.replaceRange("", lineStartPosition, lineStartPosition + 1);
                } else {
                    rSyntaxTextArea.replaceRange("%", lineStartPosition, lineStartPosition);
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    private CompletionProvider createCompletionProvider() {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();
        provider.setAutoActivationRules(true, "");

        URL[] urls = new URL[3];
        urls[0] = getClass().getResource("/openlatexstudio/tex.cwl");
        urls[1] = getClass().getResource("/openlatexstudio/latex-document.cwl");
        urls[2] = getClass().getResource("/openlatexstudio/latex-mathsymbols.cwl");

        for (URL url : urls) {
            InputStream is = null;
            try {
                is = url.openStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith("#")) {
                        provider.addCompletion(new BasicCompletion(provider, line.substring(1)));
                    }
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }

        return provider;
    }

    private void displayCloudStatus() {

        boolean isConnected = false;
        String message;
        DbxAccountInfo info = null;

        // Check Dropbox connection
        DbxClient client = DbxUtil.getDbxClient();
        if (client != null) {
            String userToken = client.getAccessToken();
            if (userToken != null && !userToken.isEmpty()) {
                try {
                    info = client.getAccountInfo();
                    isConnected = true;
                } catch (DbxException ex) {
                    // simply stay working locally.
                }
            }
        }

        if (isConnected) {
            message = "Connected to Dropbox account as " + info.displayName + ".";
            Cloud.getInstance().setStatus(Cloud.Status.DBX_CONNECTED, " (" + info.displayName + ")");
        } else {
            message = "Working locally.";
            Cloud.getInstance().setStatus(Cloud.Status.DISCONNECTED);
        }

        LOGGER.log(message);
    }

    public UnsavedWorkState canOpen() {

        if (isModified() && !isPreviewDisplayed()) {
            int userChoice = JOptionPane.showConfirmDialog(this, "This document has been modified. Do you want to save it first?", "Save document", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (userChoice == JOptionPane.YES_OPTION) {
                return UnsavedWorkState.SAVE_AND_OPEN;
            } else if (userChoice == JOptionPane.NO_OPTION) {
                return UnsavedWorkState.OPEN;
            } else {
                return UnsavedWorkState.CANCEL;
            }

        } else {
            return UnsavedWorkState.OPEN;
        }
    }
}
