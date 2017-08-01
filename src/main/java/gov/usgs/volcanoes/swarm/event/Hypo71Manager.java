package gov.usgs.volcanoes.swarm.event;

import gov.usgs.volcanoes.core.contrib.hypo71.ControlCard;
import gov.usgs.volcanoes.core.contrib.hypo71.CrustalModel;
import gov.usgs.volcanoes.core.contrib.hypo71.Hypo71;
import gov.usgs.volcanoes.core.contrib.hypo71.PhaseRecord;
import gov.usgs.volcanoes.core.contrib.hypo71.Station;
import gov.usgs.volcanoes.core.quakeml.Pick;
import gov.usgs.volcanoes.swarm.Swarm;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.JOptionPane;

public class Hypo71Manager {

  public static double[] WEIGHT_THRESHOLD_SECONDS = new double[] { 0.01, 0.02, 0.1, 0.2 };
  protected String defaultCrustalModel = "3.30 0.0\n5.00 1.0\n5.70 4.0\n6.70 15.0\n8.00 25.0";
  protected String crustalModelFileName = "DefaultVelocityModel.txt";
  protected String description = "";
  protected Queue<Station> stationsList = new LinkedList<Station>();
  protected Queue<CrustalModel> crustalModelList = new LinkedList<CrustalModel>();
  protected Queue<PhaseRecord> phaseRecordsList = new LinkedList<PhaseRecord>();
  protected ControlCard controlCard = new ControlCard(0, 5.0, 50.0, 100.0, 1.78, 2, 1, 18, 1, 1, 0, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0);;
  protected Hypo71 hypo71 = new Hypo71();
  private char prevIns = ' ';
  private char prevIew = ' ';

  public Hypo71Manager() {
    // TODO Auto-generated constructor stub
  }

  /**
   * Clear input data.
   */
  public void clear() {
    stationsList.clear();
    crustalModelList.clear();
    phaseRecordsList.clear();
    hypo71 = new Hypo71();
  }
  
  /**
   * Calculate hypocenter using hypo71.
   * @param inputFile hypo71 input file
   * @return true if hypo71 ran without errors
   * @throws IOException IO exception
   * @throws ParseException parse exception
   */
  public boolean calculate(String inputFile) throws IOException, ParseException {
    try {
      hypo71.calculateHypo71(description, null, stationsList, crustalModelList, controlCard,
          phaseRecordsList, inputFile);
    } catch (Exception e) {
      e.printStackTrace();
      String message = "Error running Hypo71:\n" + e.getMessage();
      JOptionPane.showMessageDialog(Swarm.getApplicationFrame(), message);
      return false;
    }
    return true;
  }
  
  /**
   * Add station to station list.
   * @param name station name
   * @param latitude station latitude in DD
   * @param longitude station longitude in DD
   * @param elevation in meters
   * @param delay station delay
   * @param fmag station correction for coda magnitude (FMAG)
   * @param xmag station correction for local magnitude (XMAG)
   * @param sysNum system number assigned to station so that the frequency response curve 
   *               of the seismometer and preamp is specified for the amplitude magnitude calculation.
   *               (E.g. use 0 for Wood-Anderson)
   */
  public void addStation(String name, double latitude, double longitude, double elevation, double delay, double fmag,
      double xmag, int sysNum) throws IllegalArgumentException {
    // stationsList.add(new Station(' ', "SR01", 38, 42.55f, ' ', 122, 59.17f,
    // ' ', 0, -0.15f, 0.4f, 0.25f, 8, 0.0f, 0.0f, 0, 0, 0));
    //SR013842.55 12259.17      -0.15     0.40   0.25 8

    //Formatter formatter = new Formatter(new StringBuilder(), Locale.US);
    //String nsta = formatter.format("%1$4s", name).toString();
    char ins = 'N';
    if (latitude < 0) {
      ins = 'S';
      latitude *= -1;
    }
    double lat1 = Math.floor(latitude);
    double lat2 = latitude - lat1;
    char iew = 'E';
    if (longitude < 0) {
      iew = 'W';
      longitude *= -1;
    }
    double lon1 = Math.floor(longitude);
    double lon2 = longitude - lon1;

    if ((prevIns != ' ' && ins != prevIns) || (prevIew != ' ' && iew != prevIew)) {
      String message = "All stations must be located in the same hemisphere.\n";
      message += name + "'s hemisphere differs from previous station's.";
      throw new IllegalArgumentException(message);
    } else {
      prevIns = ins;
      prevIew = iew;
    }
    
    Station station = new Station(' ', name, (int) lat1, lat2, ins, (int) lon1, lon2, iew, (int)elevation, delay,
        fmag, xmag, sysNum, 0f, 0f, 0, 0, 0);
    stationsList.add(station);
    //formatter.close();
    
  }
  
  /**
   * Create and add phase record.
   * 
   * @param station station name
   * @param pPick P pick
   * @param sPick S pick
   */
  public void addPhaseRecord(String station, Pick pPick, Pick sPick) {

    if (pPick == null) {
      throw new IllegalArgumentException("P pick is required for phase record.");
    }
    // phaseRecordsList.add(new PhaseRecord("SR01", "IPD0", 0.0f, 69100512, 6,
    // 51.22f, 0.0f, "", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, "", 0.0f, 0.0f,
    // "1.22", 'D', "", "SR01IPD0 691005120651.22", ' ', "IPD0"));
    
    // PRMK
    String pOnset = pPick.getOnset().toString().substring(0, 1).toUpperCase();
    String pMotion = getMotion(pPick.getPolarity());
    String pWeight = getWeightFromUncertainty(pPick.getTimeQuantity().getUncertainty())+".00";
    String pRemark = pOnset + pMotion + "P" + pWeight;

    // P-arrival time
    SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmmss.ss");
    String pTime = df.format(pPick.getTimeQuantity().getValue());
    String pHour = pTime.substring(0, 8);
    String pMin = pTime.substring(8, 10);
    String pSec = pTime.substring(10);
    // S-arrival time and remark
    float sSec = 0.0f;
    String sRemark = "";
    if (sPick != null) {
      sSec = (float) (Double.parseDouble(pSec) + (sPick.getTime() - sPick.getTime()) / 1000);
      // SRMK
      String sOnset = sPick.getOnset().toString().substring(0, 1).toUpperCase();
      String sMotion = getMotion(sPick.getPolarity());
      String sWeight = getWeightFromUncertainty(sPick.getTimeQuantity().getUncertainty())+".00";
      sRemark = sOnset + sMotion + "S" + sWeight;
    }
    // icard
    if (station.length() > 4) {
      station = station.substring(0, 4);
    }
    String icard = station + pRemark + " " + pTime;
    
    // create phase record
    PhaseRecord phaseRecord = new PhaseRecord(station, pRemark, 0.0f, 
        Integer.parseInt(pHour), Integer.parseInt(pMin),
        Float.parseFloat(pSec), sSec, sRemark, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, "", 0.0f,
        0.0f, "", pMotion.charAt(0), "", icard, ' ', pRemark);
    phaseRecordsList.add(phaseRecord);    
  }
  
  /**
   * Get hypo71 weight based on pick uncertainty.
   * 
   * @param seconds uncertainty in seconds
   * @return 0 to 4 (to 2 decimal places)
   */
  private int getWeightFromUncertainty(double seconds) {
    int weight = 4;
    if (Double.isNaN(seconds)) {
      return weight;
    }
    for (int i = 0; i < WEIGHT_THRESHOLD_SECONDS.length; i++) {
      if (seconds <= WEIGHT_THRESHOLD_SECONDS[i]) {
        weight = i;
        break;
      }
    }
    return weight;
  }
  
  /**
   * Get first motion direction of an arrival.
   * 
   * @param polarity pick polarity
   * @return U, D, or blank
   */
  private static String getMotion(Pick.Polarity polarity) {
    String motion = " ";
    if (polarity == null) {
      return motion;
    }
    switch (polarity) {
      case POSITIVE:
        motion = "U";
        break;
      case NEGATIVE:
        motion = "D";
        break;
      default:
        break;
    }
    return motion;
  }
  
  /**
   * Load crustal model from crustal model file.
   */
  public void loadCrustalModelFromFile() {
    crustalModelList.clear();
    try {
      FileReader fileReader = new FileReader(crustalModelFileName);
      BufferedReader bufReader = new BufferedReader(fileReader);
      while (bufReader.ready()) {
        String line = bufReader.readLine();
        System.out.println(line);
        String[] data = line.trim().split("\\s+");
        double velocity = Double.valueOf(data[0]);
        double depth = Double.valueOf(data[1]);
        CrustalModel crustalModel = new CrustalModel(velocity, depth);
        crustalModelList.add(crustalModel);
      }
      bufReader.close();
      fileReader.close();
    } catch (FileNotFoundException e) {
      try {
        // Write out to default crustal model file
        FileWriter fileWriter = new FileWriter(crustalModelFileName);
        fileWriter.write(defaultCrustalModel);
        fileWriter.flush();
        fileWriter.close();
        // Load default crustal model
        String[] line = defaultCrustalModel.split("\n");
        for (String l : line) {
          String[] data = l.trim().split("\\s");
          double velocity = Double.valueOf(data[0]);
          double depth = Double.valueOf(data[1]);
          CrustalModel crustalModel = new CrustalModel(velocity, depth);
          crustalModelList.add(crustalModel);
        }
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
