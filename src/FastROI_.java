import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageStatistics;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 */
public class FastROI_ implements PlugIn {
	private JPanel panel;
	private File currentDirectory = new File(".");
	private Map<StatisticId, Statistic> statsMap = new TreeMap<StatisticId, Statistic>();
	private ResultsTable rt = null;
	
	public void run(String arg) {
		displayGui();
	}

	private void analyzeROIs() {
		ImagePlus image = WindowManager.getCurrentImage();
		RoiManager roiManager = RoiManager.getInstance();
		Map<String, Roi> rois = new HashMap<String, Roi>();

		
		if (roiManager != null) {
			rois = roiManager.getROIs();
		} else {
			Roi roi = image.getRoi();
			if (roi != null) {
				rois.put(roi.getName(), roi);
			}
		}

		for (String name : rois.keySet()) {
			Roi roi = rois.get(name);
			statsMap.put(new StatisticId(image, roi), new Statistic(image, roi));
		}

		rt = new ResultsTable();
		
		for (StatisticId id : statsMap.keySet()) {
			Statistic stat = statsMap.get(id);
			rt.incrementCounter();
			rt.addLabel("ROI", stat.roiName);
			rt.addValue("Slice", stat.slice);
			rt.addValue("Mean", stat.mean);
			rt.addValue("StdDev", stat.stdDev);
			rt.addValue("Min", stat.min);
			rt.addValue("Max", stat.max);
		}
		rt.show("ROI Measurements");
	}

	private void saveMeasurements(File directory) {
		Map<String, List<Statistic>> roisMap = new HashMap<String, List<Statistic>>();
		for (StatisticId id : statsMap.keySet()) {
			List<Statistic> stats;
			if (roisMap.containsKey(id.roiName)) {
				stats = roisMap.get(id.roiName);
			} else {
				stats = new ArrayList<Statistic>();
				roisMap.put(id.roiName, stats);
			}
			stats.add(statsMap.get(id));
		}
		for (String roiName : roisMap.keySet()) {
			File roiMeasurements = new File(directory + File.separator + roiName + "_measurements.tsv");
			try {
				FileWriter writer = new FileWriter(roiMeasurements);
				writer.append("slice\tmean\tstdDev\n");
				for (Statistic stat : roisMap.get(roiName)) {
					writer.append(stat.slice+"\t"+stat.mean+"\t"+stat.stdDev+"\n");
				}
				writer.close();
			} catch (IOException e) {
				IJ.error("Failed to write to file "+roiMeasurements);
			}
		}
	}
	
	private void updateRoiSliceNumbers() {
		RoiManager roiManager = RoiManager.getInstance();
		int currentSlice = WindowManager.getCurrentImage().getCurrentSlice();

		if (roiManager != null) {
			Map<String, Roi> rois = roiManager.getROIs();
			for (Roi roi : rois.values()) {
				System.out.println("Changing roi " + roi.getName() + " position from " + roi.getPosition() + " to " + currentSlice);
				roi.setPosition(currentSlice);
			}
		}
	}

	private void displayGui() {
		final JFrame frame = new JFrame("FastROIs");
		frame.getContentPane().setLayout(new FlowLayout());
		panel = new JPanel();
		panel.setLayout(new GridLayout(4, 4, 5, 5));

		JButton measureButton = new JButton("Measure");
		measureButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				analyzeROIs();
			}
		});
		panel.add(measureButton);

		/*
		 * First take a measurement, increment the slice and move all ROIs to that slice number
		 */
		JButton measurePlusButton = new JButton("Measure+");
		measurePlusButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				analyzeROIs();
				IJ.run("Next Slice [>]");
				updateRoiSliceNumbers();
			}
		});
		panel.add(measurePlusButton);

		JButton updateRoisButton = new JButton("Update ROIs");
		updateRoisButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateRoiSliceNumbers();
			}
		});
		panel.add(updateRoisButton);

		final JButton pickOutputFile = new JButton("Save");
		pickOutputFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				fileChooser.setCurrentDirectory(currentDirectory);
				int returnVal = fileChooser.showOpenDialog(frame);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					File dir = fileChooser.getSelectedFile();
					currentDirectory = dir;
					saveMeasurements(dir);
				}
			}
		});
		panel.add(pickOutputFile);
		
		final JButton clearButton = new JButton("Clear");
		clearButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				statsMap.clear();
				
			}
		});
		panel.add(clearButton);

		frame.getContentPane().add(panel);
		frame.pack();
		frame.setMinimumSize(frame.getSize());
		frame.setVisible(true);
		
		if (RoiManager.getInstance() == null) {
			new RoiManager();
		}
	}
}

class Statistic {
	final public double mean, stdDev, min, max;
	final public String imageName, roiName;
	final public int slice;

	public Statistic(final ImagePlus image, final Roi roi) {
		Roi originalRoi = image.getRoi();
		image.setRoi(roi);
		ImageStatistics stats = image.getStatistics(Analyzer.getMeasurements());
		mean = stats.mean;
		stdDev = stats.stdDev;
		min = stats.min;
		max = stats.max;
		roiName = roi.getName();
		imageName = image.getTitle();
		slice = image.getCurrentSlice();
		image.setRoi(originalRoi);
	}
}

class StatisticId implements Comparable<StatisticId> {
	final public String imageName, roiName;
	final public int slice;

	public StatisticId(ImagePlus image, Roi roi) {
		imageName = image.getTitle();
		roiName = roi.getName();
		slice = image.getCurrentSlice();
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash *= 31 + imageName.hashCode();
		hash *= 31 + roiName.hashCode();
		hash *= 31 + slice;
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		boolean result = false;
		if (obj == this) {
			result = true;
		} else if (obj instanceof StatisticId) {
			StatisticId that = (StatisticId) obj;
			result = that.imageName.equals(this.imageName) && that.roiName.equals(this.roiName) && that.slice == this.slice;

		}
		return result;
	}

	@Override
	public int compareTo(StatisticId o) {
		int cmp = imageName.compareTo(o.imageName);
		if (cmp != 0) {
			return cmp;
		}
		cmp = slice - o.slice;
		if (cmp != 0) {
			return cmp;
		}
		return roiName.compareTo(o.roiName);
	}

	@Override
	public String toString() {
		return "ImageName: " + imageName + " Slice: " + slice + " RoiName: " + roiName;
	}
}