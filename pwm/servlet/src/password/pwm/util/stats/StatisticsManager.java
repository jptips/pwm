/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util.stats;

import org.apache.commons.csv.CSVPrinter;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.StatsPublishBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class StatisticsManager implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(StatisticsManager.class);

    private static final int DB_WRITE_FREQUENCY_MS = 60 * 1000;  // 1 minutes

    private static final String DB_KEY_VERSION = "STATS_VERSION";
    private static final String DB_KEY_CUMULATIVE = "CUMULATIVE";
    private static final String DB_KEY_INITIAL_DAILY_KEY = "INITIAL_DAILY_KEY";
    private static final String DB_KEY_PREFIX_DAILY = "DAILY_";
    private static final String DB_KEY_TEMP = "TEMP_KEY";

    private static final String DB_VALUE_VERSION = "1";

    public static final String KEY_CURRENT = "CURRENT";
    public static final String KEY_CUMULATIVE = "CUMULATIVE";
    public static final String KEY_CLOUD_PUBLISH_TIMESTAMP = "CLOUD_PUB_TIMESTAMP";

    private LocalDB localDB;

    private DailyKey currentDailyKey = new DailyKey(new Date());
    private DailyKey initialDailyKey = new DailyKey(new Date());

    private Timer daemonTimer;

    private final StatisticsBundle statsCurrent = new StatisticsBundle();
    private StatisticsBundle statsDaily = new StatisticsBundle();
    private StatisticsBundle statsCummulative = new StatisticsBundle();
    private Map<String, EventRateMeter> epsMeterMap = new HashMap<>();

    private PwmApplication pwmApplication;

    private STATUS status = STATUS.NEW;



    private final Map<String,StatisticsBundle> cachedStoredStats = new LinkedHashMap<String,StatisticsBundle>() {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, StatisticsBundle> eldest) {
            return this.size() > 50;
        }
    };

    public StatisticsManager() {
    }

    public synchronized void incrementValue(final Statistic statistic) {
        statsCurrent.incrementValue(statistic);
        statsDaily.incrementValue(statistic);
        statsCummulative.incrementValue(statistic);
    }

    public synchronized void updateAverageValue(final Statistic statistic, final long value) {
        statsCurrent.updateAverageValue(statistic,value);
        statsDaily.updateAverageValue(statistic,value);
        statsCummulative.updateAverageValue(statistic,value);
    }

    public Map<String,String> getStatHistory(final Statistic statistic, final int days) {
        final Map<String,String> returnMap = new LinkedHashMap<>();
        DailyKey loopKey = currentDailyKey;
        int counter = days;
        while (counter > 0) {
            final StatisticsBundle bundle = getStatBundleForKey(loopKey.toString());
            if (bundle != null) {
                final String key = (new SimpleDateFormat("MMM dd")).format(loopKey.calendar().getTime());
                final String value = bundle.getStatistic(statistic);
                returnMap.put(key,value);
            }
            loopKey = loopKey.previous();
            counter--;
        }
        return returnMap;
    }

    public StatisticsBundle getStatBundleForKey(final String key) {
        if (key == null || key.length() < 1 || KEY_CUMULATIVE.equals(key) ) {
            return statsCummulative;
        }

        if (KEY_CURRENT.equals(key)) {
            return statsCurrent;
        }

        if (currentDailyKey.toString().equals(key)) {
            return statsDaily;
        }

        if (cachedStoredStats.containsKey(key)) {
            return cachedStoredStats.get(key);
        }

        if (localDB == null) {
            return null;
        }

        try {
            final String storedStat = localDB.get(LocalDB.DB.PWM_STATS, key);
            final StatisticsBundle returnBundle;
            if (storedStat != null && storedStat.length() > 0) {
                returnBundle = StatisticsBundle.input(storedStat);
            } else {
                returnBundle = new StatisticsBundle();
            }
            cachedStoredStats.put(key, returnBundle);
            return returnBundle;
        } catch (LocalDBException e) {
            LOGGER.error("error retrieving stored stat for " + key + ": " + e.getMessage());
        }

        return null;
    }

    public Map<DailyKey,String> getAvailableKeys(final Locale locale) {
        final DateFormat dateFormatter = SimpleDateFormat.getDateInstance(SimpleDateFormat.DEFAULT, locale);
        final Map<DailyKey,String> returnMap = new LinkedHashMap<DailyKey,String>();

        // add current time;
        returnMap.put(currentDailyKey, dateFormatter.format(new Date()));

        // if now historical data then we're done
        if (currentDailyKey.equals(initialDailyKey)) {
            return returnMap;
        }

        DailyKey loopKey = currentDailyKey;
        int safetyCounter = 0;
        while (!loopKey.equals(initialDailyKey) && safetyCounter < 5000) {
            final Calendar c = loopKey.calendar();
            final String display = dateFormatter.format(c.getTime());
            returnMap.put(loopKey,display);
            loopKey = loopKey.previous();
            safetyCounter++;
        }
        return returnMap;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();

        for (final Statistic m : Statistic.values()) {
            sb.append(m.toString());
            sb.append("=");
            sb.append(statsCurrent.getStatistic(m));
            sb.append(", ");
        }

        if (sb.length() > 2) {
            sb.delete(sb.length() -2 , sb.length());
        }

        return sb.toString();
    }

    public void init(PwmApplication pwmApplication) throws PwmException {
        for (final Statistic.EpsType type : Statistic.EpsType.values()) {
            for (final Statistic.EpsDuration duration : Statistic.EpsDuration.values()) {
                epsMeterMap.put(type.toString() + duration.toString(), new EventRateMeter(duration.getTimeDuration()));
            }
        }

        status = STATUS.OPENING;
        this.localDB = pwmApplication.getLocalDB();
        this.pwmApplication = pwmApplication;

        if (localDB == null) {
            LOGGER.error("LocalDB is not available, will remain closed");
            status = STATUS.CLOSED;
            return;
        }

        {
            final String storedCummulativeBundleStr = localDB.get(LocalDB.DB.PWM_STATS, DB_KEY_CUMULATIVE);
            if (storedCummulativeBundleStr != null && storedCummulativeBundleStr.length() > 0) {
                statsCummulative = StatisticsBundle.input(storedCummulativeBundleStr);
            }
        }

        {
            for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) {
                for (final Statistic.EpsType loopEpsDuration : Statistic.EpsType.values()) {
                    final String key = "EPS-" + loopEpsType.toString() + loopEpsDuration.toString();
                    final String storedValue = localDB.get(LocalDB.DB.PWM_STATS,key);
                    if (storedValue != null && storedValue.length() > 0) {
                        try {
                            final EventRateMeter eventRateMeter = JsonUtil.deserialize(storedValue, EventRateMeter.class);
                            epsMeterMap.put(loopEpsType.toString() + loopEpsDuration.toString(),eventRateMeter);
                        } catch (Exception e) {
                            LOGGER.error("unexpected error reading last EPS rate for " + loopEpsType + " from LocalDB: " + e.getMessage());
                        }
                    }
                }
            }

        }

        {
            final String storedInitialString = localDB.get(LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY);
            if (storedInitialString != null && storedInitialString.length() > 0) {
                initialDailyKey = new DailyKey(storedInitialString);
            }
        }

        {
            currentDailyKey = new DailyKey(new Date());
            final String storedDailyStr = localDB.get(LocalDB.DB.PWM_STATS, currentDailyKey.toString());
            if (storedDailyStr != null && storedDailyStr.length() > 0) {
                statsDaily = StatisticsBundle.input(storedDailyStr);
            }
        }

        try {
            localDB.put(LocalDB.DB.PWM_STATS, DB_KEY_TEMP, PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
        } catch (IllegalStateException e) {
            LOGGER.error("unable to write to localDB, will remain closed, error: " + e.getMessage());
            status = STATUS.CLOSED;
            return;
        }

        localDB.put(LocalDB.DB.PWM_STATS, DB_KEY_VERSION, DB_VALUE_VERSION);
        localDB.put(LocalDB.DB.PWM_STATS, DB_KEY_INITIAL_DAILY_KEY, initialDailyKey.toString());

        { // setup a timer to roll over at 0 Zula and one to write current stats every 10 seconds
            final String threadName = Helper.makeThreadName(pwmApplication, this.getClass()) + " timer";
            daemonTimer = new Timer(threadName, true);
            daemonTimer.schedule(new FlushTask(), 10 * 1000, DB_WRITE_FREQUENCY_MS);
            daemonTimer.schedule(new NightlyTask(), Helper.nextZuluZeroTime());
        }

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.RUNNING) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PUBLISH_STATS_ENABLE)) {
                long lastPublishTimestamp = pwmApplication.getInstallTime().getTime();
                {
                    final String lastPublishDateStr = localDB.get(LocalDB.DB.PWM_STATS,KEY_CLOUD_PUBLISH_TIMESTAMP);
                    if (lastPublishDateStr != null && lastPublishDateStr.length() > 0) {
                        try {
                            lastPublishTimestamp = Long.parseLong(lastPublishDateStr);
                        } catch (Exception e) {
                            LOGGER.error("unexpected error reading last publish timestamp from PwmDB: " + e.getMessage());
                        }
                    }
                }
                final Date nextPublishTime = new Date(lastPublishTimestamp + PwmConstants.STATISTICS_PUBLISH_FREQUENCY_MS + (long)PwmRandom.getInstance().nextInt(3600 * 1000));
                daemonTimer.schedule(new PublishTask(), nextPublishTime, PwmConstants.STATISTICS_PUBLISH_FREQUENCY_MS);
            }
        }

        status = STATUS.OPEN;
    }

    private void writeDbValues() {
        if (localDB != null) {
            try {
                localDB.put(LocalDB.DB.PWM_STATS, DB_KEY_CUMULATIVE, statsCummulative.output());
                localDB.put(LocalDB.DB.PWM_STATS, currentDailyKey.toString(), statsDaily.output());

                for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) {
                    for (final Statistic.EpsDuration loopEpsDuration : Statistic.EpsDuration.values()) {
                        final String key = "EPS-" + loopEpsType.toString();
                        final String mapKey = loopEpsType.toString() + loopEpsDuration.toString();
                        final String value = JsonUtil.serialize(this.epsMeterMap.get(mapKey));
                        localDB.put(LocalDB.DB.PWM_STATS, key, value);
                    }
                }
            } catch (LocalDBException e) {
                LOGGER.error("error outputting pwm statistics: " + e.getMessage());
            }
        }

    }

    private void resetDailyStats() {
        final Map<String,String> emailValues = new LinkedHashMap<>();
        for (final Statistic statistic : Statistic.values()) {
            final String key = statistic.getLabel(PwmConstants.DEFAULT_LOCALE);
            final String value = statsDaily.getStatistic(statistic);
            emailValues.put(key,value);
        }

        AlertHandler.alertDailyStats(pwmApplication, emailValues);

        currentDailyKey = new DailyKey(new Date());
        statsDaily = new StatisticsBundle();
        LOGGER.debug("reset daily statistics");
    }

    public STATUS status() {
        return status;
    }


    public void close() {
        try {
            writeDbValues();
        } catch (Exception e) {
            LOGGER.error("unexpected error closing: " + e.getMessage());
        }
        if (daemonTimer != null) {
            daemonTimer.cancel();
        }
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }


    private class NightlyTask extends TimerTask {
        public void run() {
            writeDbValues();
            resetDailyStats();
            daemonTimer.schedule(new NightlyTask(), Helper.nextZuluZeroTime());
        }
    }

    private class FlushTask extends TimerTask {
        public void run() {
            writeDbValues();
        }
    }

    private class PublishTask extends TimerTask {
        public void run() {
            try {
                publishStatisticsToCloud();
            } catch (Exception e) {
                LOGGER.error("error publishing statistics to cloud: " + e.getMessage());
            }
        }
    }

    public static class DailyKey {
        int year;
        int day;

        public DailyKey(final Date date) {
            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Zulu"));
            calendar.setTime(date);
            year = calendar.get(Calendar.YEAR);
            day = calendar.get(Calendar.DAY_OF_YEAR);
        }

        public DailyKey(final String value) {
            final String strippedValue = value.substring(DB_KEY_PREFIX_DAILY.length(),value.length());
            final String[] splitValue = strippedValue.split("_");
            year = Integer.valueOf(splitValue[0]);
            day = Integer.valueOf(splitValue[1]);
        }

        private DailyKey() {
        }

        @Override
        public String toString() {
            return DB_KEY_PREFIX_DAILY + String.valueOf(year) + "_" + String.valueOf(day);
        }

        public DailyKey previous() {
            final Calendar calendar = calendar();
            calendar.add(Calendar.HOUR,-24);
            final DailyKey newKey = new DailyKey();
            newKey.year = calendar.get(Calendar.YEAR);
            newKey.day = calendar.get(Calendar.DAY_OF_YEAR);
            return newKey;
        }

        public Calendar calendar() {
            final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Zulu"));
            calendar.set(Calendar.YEAR,year);
            calendar.set(Calendar.DAY_OF_YEAR,day);
            return calendar;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final DailyKey key = (DailyKey) o;

            if (day != key.day) return false;
            if (year != key.year) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = year;
            result = 31 * result + day;
            return result;
        }
    }

    public void updateEps(final Statistic.EpsType type, final int itemCount) {
        for (final Statistic.EpsDuration duration : Statistic.EpsDuration.values()) {
            epsMeterMap.get(type.toString() + duration.toString()).markEvents(itemCount);
        }
    }

    public BigDecimal readEps(final Statistic.EpsType type, final Statistic.EpsDuration duration) {
        return epsMeterMap.get(type.toString() + duration.toString()).readEventRate();
    }

    private void publishStatisticsToCloud()
            throws URISyntaxException, IOException, PwmUnrecoverableException {
        final StatsPublishBean statsPublishData;
        {
            final StatisticsBundle bundle = getStatBundleForKey(KEY_CUMULATIVE);
            final Map<String,String> statData = new HashMap<>();
            for (final Statistic loopStat : Statistic.values()) {
                statData.put(loopStat.getKey(),bundle.getStatistic(loopStat));
            }
            final Configuration config = pwmApplication.getConfig();
            final List<String> configuredSettings = new ArrayList<>();
            for (final PwmSetting pwmSetting : config.nonDefaultSettings()) {
                if (!pwmSetting.getCategory().hasProfiles() && !config.isDefaultValue(pwmSetting)) {
                    configuredSettings.add(pwmSetting.getKey());
                }
            }
            final Map<String,String> otherData = new HashMap<>();
            otherData.put(StatsPublishBean.KEYS.SITE_URL.toString(),config.readSettingAsString(PwmSetting.PWM_SITE_URL));
            otherData.put(StatsPublishBean.KEYS.SITE_DESCRIPTION.toString(),config.readSettingAsString(PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION));
            otherData.put(StatsPublishBean.KEYS.INSTALL_DATE.toString(),PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwmApplication.getInstallTime()));

            try {
                otherData.put(StatsPublishBean.KEYS.LDAP_VENDOR.toString(),pwmApplication.getProxyChaiProvider(config.getDefaultLdapProfile().getIdentifier()).getDirectoryVendor().toString());
            } catch (Exception e) {
                LOGGER.trace("unable to read ldap vendor type for stats publication: " + e.getMessage());
            }

            statsPublishData = new StatsPublishBean(
                    pwmApplication.getInstanceID(),
                    new Date(),
                    statData,
                    configuredSettings,
                    PwmConstants.BUILD_NUMBER,
                    PwmConstants.BUILD_VERSION,
                    otherData
            );
        }
        final URI requestURI = new URI(PwmConstants.PWM_URL_CLOUD + "/rest/pwm/statistics");
        final HttpPost httpPost = new HttpPost(requestURI.toString());
        final String jsonDataString = JsonUtil.serialize(statsPublishData);
        httpPost.setEntity(new StringEntity(jsonDataString));
        httpPost.setHeader("Accept", PwmConstants.AcceptValue.json.getHeaderValue());
        httpPost.setHeader("Content-Type", PwmConstants.ContentTypeValue.json.getHeaderValue());
        LOGGER.debug("preparing to send anonymous statistics to " + requestURI.toString() + ", data to send: " + jsonDataString);
        final HttpResponse httpResponse = PwmHttpClient.getHttpClient(pwmApplication.getConfig()).execute(httpPost);
        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException("http response error code: " + httpResponse.getStatusLine().getStatusCode());
        }
        LOGGER.info("published anonymous statistics to " + requestURI.toString());
        try {
            localDB.put(LocalDB.DB.PWM_STATS, KEY_CLOUD_PUBLISH_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        } catch (LocalDBException e) {
            LOGGER.error("unexpected error trying to save last statistics published time to LocalDB: " + e.getMessage());
        }
    }

    public int outputStatsToCsv(final OutputStream outputStream, final Locale locale, final boolean includeHeader)
            throws IOException
    {
        LOGGER.trace("beginning output stats to csv process");
        final Date startTime = new Date();

        final StatisticsManager statsManger = pwmApplication.getStatisticsManager();
        final CSVPrinter csvPrinter = Helper.makeCsvPrinter(outputStream);

        if (includeHeader) {
            final List<String> headers = new ArrayList<>();
            headers.add("KEY");
            headers.add("YEAR");
            headers.add("DAY");
            for (Statistic stat : Statistic.values()) {
                headers.add(stat.getLabel(locale));
            }
            csvPrinter.printRecord(headers);
        }

        int counter = 0;
        final Map<StatisticsManager.DailyKey, String> keys = statsManger.getAvailableKeys(PwmConstants.DEFAULT_LOCALE);
        for (final StatisticsManager.DailyKey loopKey : keys.keySet()) {
            counter++;
            final StatisticsBundle bundle = statsManger.getStatBundleForKey(loopKey.toString());
            final List<String> lineOutput = new ArrayList<>();
            lineOutput.add(loopKey.toString());
            lineOutput.add(String.valueOf(loopKey.year));
            lineOutput.add(String.valueOf(loopKey.day));
            for (final Statistic stat : Statistic.values()) {
                lineOutput.add(bundle.getStatistic(stat));
            }
            csvPrinter.printRecord(lineOutput);
        }
        
        csvPrinter.flush();
        LOGGER.trace("completed output stats to csv process; output " + counter + " records in " + TimeDuration.fromCurrent(
                startTime).asCompactString());
        return counter;
    }

    public ServiceInfo serviceInfo()
    {
        if (status() == STATUS.OPEN) {
            return new ServiceInfo(Collections.singletonList(DataStorageMethod.LOCALDB));
        } else {
            return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
        }
    }

    public static void incrementStat(
            final PwmRequest pwmRequest,
            final Statistic statistic
    )
    {
        incrementStat(pwmRequest.getPwmApplication(), statistic);
    }

    public static void incrementStat(
            final PwmApplication pwmApplication,
            final Statistic statistic
    ) {
        if (pwmApplication == null) {
            LOGGER.error("skipping requested statistic increment of " + statistic + " due to null pwmApplication");
            return;
        }

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if (statisticsManager == null) {
            LOGGER.error("skipping requested statistic increment of " + statistic + " due to null statisticsManager");
            return;
        }

        if (statisticsManager.status() != STATUS.OPEN) {
            LOGGER.trace(
                    "skipping requested statistic increment of " + statistic + " due to StatisticsManager being closed");
            return;
        }

        statisticsManager.incrementValue(statistic);
    }

}
