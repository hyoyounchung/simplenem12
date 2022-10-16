package simplenem12;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation class for SimpleNem12Parser interface
 * will parse simpleNem12File csv file and read record block in the file
 */
public class SimpleNem12ParserImpl implements SimpleNem12Parser {
    public static final String DELIMITER = ",";
    public static final int NMI_LENGTH = 10;
    public static final int RECORD_200_LENGTH = 3;
    public static final int RECORD_300_LENGTH = 4;
    public static final String RECORD_TYPE_FILE_START = "100";
    public static final String RECORD_TYPE_READ_BLOCK_START = "200";
    public static final String RECORD_TYPE_READ = "300";
    public static final String RECORD_TYPE_FILE_END = "900";

    public SimpleNem12ParserImpl() {
    }

    @Override
    public Collection<MeterRead> parseSimpleNem12(File simpleNem12File) {
        List<MeterRead> meterReads = new ArrayList<>();
        if ( Optional.ofNullable(simpleNem12File).isPresent() && !simpleNem12File.exists() )
            return meterReads;

        try (BufferedReader reader = new BufferedReader(new FileReader(simpleNem12File))) {

            List<String> lines = reader.lines().map(String::trim).collect(Collectors.toList());
            // must start with 100 and ends up with 900
            if ( lines.get(0).startsWith(RECORD_TYPE_FILE_START)
                    && lines.get(lines.size()-1).endsWith(RECORD_TYPE_FILE_END) ) {
                lines.stream()
                        .filter(line -> line.startsWith(RECORD_TYPE_READ_BLOCK_START) || line.startsWith(RECORD_TYPE_READ))
                        //.peek(line -> System.out.println(line))
                        .forEach(line -> {
                            if ( line.startsWith(RECORD_TYPE_READ_BLOCK_START) ) {  // new read block
                                this.initMeterRead(line).ifPresent(meterReads::add);
                            } else if ( line.startsWith(RECORD_TYPE_READ) ) {   // volume reads in read block
                                if (meterReads.size() > 0)
                                    this.buildVolumeForMeterRead(line, meterReads.get(meterReads.size() - 1));
                            }
                        });
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return meterReads;
    }

    /**
     * Works on 200 record type
     *
     * @param meter200Line start of reader block
     * @return  <code>MeterRead</code> that represents a meter reader on site
     */
    private Optional<MeterRead> initMeterRead(String meter200Line) {
        String[] values = meter200Line.split(DELIMITER);
        //NMI should be 10 chars long and 200 line needs to have 3 values
        if ( values[1].trim().length() == NMI_LENGTH && values.length == RECORD_200_LENGTH )
            return Optional.of(new MeterRead(values[1].trim(), EnergyUnit.valueOf(values[2].trim())));

        return Optional.empty();
    }

    /**
     * adds new volume to current nmi meterRead
     *
     * @param meter300Line volume of meter read for a particular date
     * @param meterRead represents a meter read with date & volume data
     */
    private void buildVolumeForMeterRead(String meter300Line, MeterRead meterRead) {
        if (!Optional.ofNullable(meterRead).isPresent()) {
            System.out.println("MeterRead is missing for current read!");
            return;
        }

        String[] values = meter300Line.split(DELIMITER);
        if (values.length == RECORD_300_LENGTH) {   // 300 record needs to have 4 values
            // work on date key - yyyyMMdd
            LocalDate readDate = LocalDate.parse(values[1].trim(), DateTimeFormatter.ofPattern("yyyyMMdd"));
            // build new meterVolume
            MeterVolume meterVolume = new MeterVolume(new BigDecimal(values[2].trim()), Quality.valueOf(values[3].trim()));
            meterRead.getVolumes().put(readDate, meterVolume);
        }
    }
}
