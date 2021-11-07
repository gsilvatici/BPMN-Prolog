package xmlparser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;


import javax.swing.*;

public class MainClass implements ActionListener {
	
    JFrame mainWindow;
    JPanel iconPanel;
    JPanel areaPanel;
    JButton select_file;
    ArrayList<JButton> process;
    JPanel input;
    Font font= new Font("Futura", Font.PLAIN, 20);
    
    public void createMainWindow() {
        mainWindow = new JFrame("Process Diagram Parser");
        mainWindow.setSize(1000,730);
        mainWindow.setVisible(true);        
        //creation of a main icon panel
        iconPanel = new JPanel();
        iconPanel.setBackground(new Color(34, 57, 66));
        iconPanel.setPreferredSize(new Dimension(250,200));
        mainWindow.add(iconPanel,BorderLayout.WEST);
        
        //creation of a work area panel
        Color color=new Color(230, 230, 230);
        areaPanel = new JPanel();
        areaPanel.setBackground(color);
//        areaPanel.setPreferredSize(new Dimension(600,600));
        mainWindow.add(areaPanel);
        
        //choose file button
        select_file = new JButton("Select BPMN file");
        select_file.setVisible(true);
        select_file.setEnabled(true);
        select_file.setBackground(new Color(175, 175, 175));
        iconPanel.setLayout(null);
        
        select_file.setBounds(50, 200, 130, 40);
//        select_file.setBorder(new RoundedBorder(15));
        
        
        iconPanel.add(select_file);

//        ontologies = new ArrayList<>();
        process = new ArrayList<>();
        
        select_file.addActionListener(this);
    }
    
    
    public void createActivity() {
        areaPanel.revalidate();
        areaPanel.removeAll();
        areaPanel.repaint();
        
        //creating a input panel
        input = new JPanel();
        input.setPreferredSize(new Dimension(350,800));
        input.setBackground(Color.LIGHT_GRAY);
        input.setVisible(true);
        areaPanel.add(input);

        //setting labels
        JLabel in_lb = new JLabel("Generated Prolog files");
        in_lb.setFont(font);
        in_lb.setBorder(BorderFactory.createEmptyBorder(8, 200, 10, 190));
        in_lb.setForeground(Color.WHITE);
        in_lb.setOpaque(true);
        in_lb.setBackground(Color.BLACK);
        input.add(in_lb);   
    }
    
    
    
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		MainClass main = new MainClass();
        main.createMainWindow();
        main.createActivity();
//        main.createView();
//		parseDiagram("C:\\Users\\gabri\\Desktop\\selected ones\\online shopping process.bpmn");
		
	}
	
	public void parseDiagram(String input) {
		

//		String inputFileName = input;
		String inputFileName = "C:\\Users\\gabri\\Desktop\\selected ones\\online shopping process.bpmn";
		String outputFileName = null;
		
		boolean flag=true;
		
		if (inputFileName.endsWith(".xml")) {
			outputFileName = inputFileName.substring(0, inputFileName.length() - 4) + ".pl";
		} else if (inputFileName.endsWith(".bpmn")) {
			outputFileName = inputFileName.substring(0, inputFileName.length() - 5) + ".pl";
		} else {
			System.out.println("File extension unknown. Please select a .xml or .bpmn file.");
			flag=false;
		}
		
		if(flag){
			
			File outputFile = new File(outputFileName);
			FileWriter fstream=null;
			try {
				fstream = new FileWriter(outputFile.getAbsoluteFile());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			BufferedWriter out = new BufferedWriter(fstream);
			
			File inputFile = new File(inputFileName);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	        DocumentBuilder dBuilder=null;
	        
			try {
				dBuilder = dbFactory.newDocumentBuilder();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	        Document doc = null;
	        
			try {
				doc = dBuilder.parse(inputFile);
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	        doc.getDocumentElement().normalize();
	        
	        Parser.mainParser(doc, out);
	        
	        System.out.println("Rules: " + Parser.rulesCount);
	        System.out.println("Facts: " + Parser.facts);
	        
	        try {
	        	
				out.flush();
				out.close();
			} catch (IOException e) {
				
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	        
	        System.out.println("Parsing complete. Prolog code available at : "+outputFile);
	        
		}	
	}
	
	
	
    public void createView() throws IOException{
        areaPanel.revalidate();
        areaPanel.removeAll();
        areaPanel.repaint();        
    }
	
	@Override
	public void actionPerformed(ActionEvent ae) {
		// TODO Auto-generated method stub

    	
        if(ae.getSource() == select_file){
            JFileChooser chooser = new JFileChooser();

            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setDialogTitle("Choose the Model File");
            chooser.setApproveButtonText("Select");

            chooser.showOpenDialog(null);

            String projectPath = chooser.getSelectedFile().getAbsolutePath();
            String projectname = chooser.getSelectedFile().getName();
            
            //setting process names            
            JButton btn = new JButton(projectname);
            btn.setPreferredSize(new Dimension(200,40));
            btn.setBorder(BorderFactory.createLoweredSoftBevelBorder());
            btn.addActionListener(this);
            process.add(btn);
            input.add(btn);
            
            btn.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e)
                {
                    //Execute when button is pressed
                	
                }
            });    
            
            input.revalidate();
            input.repaint();
            
            try {
				this.parseDiagram(projectPath);
			} catch (Exception e) {
				System.out.println("System exception: unreachable file path");
			}  
             
            
//            DiagramParserC dp = new DiagramParserC();
//            try {
//				ontologies.add(dp.owlFileGenerator(proyectPath, projectname));
//				//this is for generating and "output.owl" with merged ontologies
////				om = new OntologyMerger(ontologies, "output");
//			} catch (OWLOntologyCreationException | OWLOntologyStorageException | IOException e) {
//				System.out.println("System exception: unreachable file path");
//			}           
        } 
		
	}

}
