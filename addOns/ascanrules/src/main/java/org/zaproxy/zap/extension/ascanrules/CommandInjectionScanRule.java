/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2013 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.ascanrules;

import static org.zaproxy.zap.extension.ascanrules.utils.Constants.NULL_BYTE_CHARACTER;

import java.io.IOException;
import java.net.SocketException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.AbstractAppParamPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.addon.commonlib.CommonAlertTag;
import org.zaproxy.zap.model.Tech;
import org.zaproxy.zap.model.TechSet;
import org.zaproxy.zap.model.Vulnerabilities;
import org.zaproxy.zap.model.Vulnerability;

/**
 * Active scan rule for Command Injection testing and verification.
 * https://owasp.org/www-community/attacks/Command_Injection
 *
 * @author yhawke (2013)
 * @author kingthorin+owaspzap@gmail.com (2015)
 */
public class CommandInjectionScanRule extends AbstractAppParamPlugin {

    /** Prefix for internationalised messages used by this rule */
    private static final String MESSAGE_PREFIX = "ascanrules.commandinjection.";

    // *NIX OS Command constants
    private static final String NIX_TEST_CMD = "cat /etc/passwd";
    private static final Pattern NIX_CTRL_PATTERN = Pattern.compile("root:.:0:0");
    // Dot used to match 'x' or '!' (used in AIX)

    // Windows OS Command constants
    private static final String WIN_TEST_CMD = "type %SYSTEMROOT%\\win.ini";
    private static final Pattern WIN_CTRL_PATTERN = Pattern.compile("\\[fonts\\]");

    // PowerShell Command constants
    private static final String PS_TEST_CMD = "get-help";
    private static final Pattern PS_CTRL_PATTERN =
            Pattern.compile("(?:\\sGet-Help)(?i)|cmdlet|get-alias");

    // Useful if space char isn't allowed by filters
    // http://www.blackhatlibrary.net/Command_Injection

    // OS Command payloads for command Injection testing
    private static final Map<String, Pattern> NIX_OS_PAYLOADS = new LinkedHashMap<>();
    private static final Map<String, Pattern> WIN_OS_PAYLOADS = new LinkedHashMap<>();
    private static final Map<String, Pattern> PS_PAYLOADS = new LinkedHashMap<>();

    private static final Map<String, String> ALERT_TAGS =
            CommonAlertTag.toMap(
                    CommonAlertTag.OWASP_2021_A03_INJECTION,
                    CommonAlertTag.OWASP_2017_A01_INJECTION,
                    CommonAlertTag.WSTG_V42_INPV_12_COMMAND_INJ);

    static {
        // No quote payloads
        NIX_OS_PAYLOADS.put(NIX_TEST_CMD, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("&" + NIX_TEST_CMD + "&", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put(";" + NIX_TEST_CMD + ";", NIX_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put(WIN_TEST_CMD, WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("&" + WIN_TEST_CMD, WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("|" + WIN_TEST_CMD, WIN_CTRL_PATTERN);
        PS_PAYLOADS.put(PS_TEST_CMD, PS_CTRL_PATTERN);
        PS_PAYLOADS.put(";" + PS_TEST_CMD, PS_CTRL_PATTERN);

        // Double quote payloads
        NIX_OS_PAYLOADS.put("\"&" + NIX_TEST_CMD + "&\"", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("\";" + NIX_TEST_CMD + ";\"", NIX_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("\"&" + WIN_TEST_CMD + "&\"", WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("\"|" + WIN_TEST_CMD, WIN_CTRL_PATTERN);
        PS_PAYLOADS.put("\";" + PS_TEST_CMD, PS_CTRL_PATTERN);

        // Single quote payloads
        NIX_OS_PAYLOADS.put("'&" + NIX_TEST_CMD + "&'", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("';" + NIX_TEST_CMD + ";'", NIX_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("'&" + WIN_TEST_CMD + "&'", WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("'|" + WIN_TEST_CMD, WIN_CTRL_PATTERN);
        PS_PAYLOADS.put("';" + PS_TEST_CMD, PS_CTRL_PATTERN);

        // Special payloads
        NIX_OS_PAYLOADS.put("\n" + NIX_TEST_CMD + "\n", NIX_CTRL_PATTERN); // force enter
        NIX_OS_PAYLOADS.put("`" + NIX_TEST_CMD + "`", NIX_CTRL_PATTERN); // backtick execution
        NIX_OS_PAYLOADS.put("||" + NIX_TEST_CMD, NIX_CTRL_PATTERN); // or control concatenation
        NIX_OS_PAYLOADS.put("&&" + NIX_TEST_CMD, NIX_CTRL_PATTERN); // and control concatenation
        NIX_OS_PAYLOADS.put("|" + NIX_TEST_CMD + "#", NIX_CTRL_PATTERN); // pipe & comment
        // FoxPro for running os commands
        WIN_OS_PAYLOADS.put("run " + WIN_TEST_CMD, WIN_CTRL_PATTERN);
        PS_PAYLOADS.put(";" + PS_TEST_CMD + " #", PS_CTRL_PATTERN); // chain & comment

        // uninitialized variable waf bypass
        String insertedCMD = insertUninitVar(NIX_TEST_CMD);
        // No quote payloads
        NIX_OS_PAYLOADS.put("&" + insertedCMD + "&", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put(";" + insertedCMD + ";", NIX_CTRL_PATTERN);
        // Double quote payloads
        NIX_OS_PAYLOADS.put("\"&" + insertedCMD + "&\"", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("\";" + insertedCMD + ";\"", NIX_CTRL_PATTERN);
        // Single quote payloads
        NIX_OS_PAYLOADS.put("'&" + insertedCMD + "&'", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("';" + insertedCMD + ";'", NIX_CTRL_PATTERN);
        // Special payloads
        NIX_OS_PAYLOADS.put("\n" + insertedCMD + "\n", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("`" + insertedCMD + "`", NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("||" + insertedCMD, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("&&" + insertedCMD, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("|" + insertedCMD + "#", NIX_CTRL_PATTERN);

        // Used for *nix
        // OS_PAYLOADS.put("\"|\"ld", null);
        // OS_PAYLOADS.put("'|'ld", null);

        /**
         * Null Byte Payloads. Say NIX OS is there and original input is "image". Now Vulnerable
         * application is executing command as "cat" + <input value>+ ".jpeg". If ZAP payload is
         * ";cat /etc/passwd" then the final value passed to Vulnerable application is "image;cat
         * /etc/passwd" and Command executed by application is "cat image;cat /etc/passwd.jpeg" and
         * it will not succeed but if we add null byte to the payload then command executed by
         * application will be "cat image;cat /etc/passwd\0.jpeg" and hence .jpeg will be ignored
         * and attack will succeed. Here we are not adding null byte before the payload i.e.
         * "image\0;cat /etc/passwd" reason is then the underline C/C++ utility stops execution as
         * it finds null byte. More information:
         * http://projects.webappsec.org/w/page/13246949/Null%20Byte%20Injection
         */
        // No quote payloads
        NIX_OS_PAYLOADS.put(";" + NIX_TEST_CMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("&" + NIX_TEST_CMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);

        WIN_OS_PAYLOADS.put("&" + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("|" + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);

        // Double quote payloads
        NIX_OS_PAYLOADS.put("\"&" + NIX_TEST_CMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("\";" + NIX_TEST_CMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);

        WIN_OS_PAYLOADS.put("\"&" + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("\"|" + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);

        // Single quote payloads
        NIX_OS_PAYLOADS.put("'&" + NIX_TEST_CMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("';" + NIX_TEST_CMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);

        WIN_OS_PAYLOADS.put("'&" + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);
        WIN_OS_PAYLOADS.put("'|" + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);

        // Special payloads
        NIX_OS_PAYLOADS.put(
                "||" + NIX_TEST_CMD + NULL_BYTE_CHARACTER,
                NIX_CTRL_PATTERN); // or control concatenation
        NIX_OS_PAYLOADS.put(
                "&&" + NIX_TEST_CMD + NULL_BYTE_CHARACTER,
                NIX_CTRL_PATTERN); // and control concatenation
        // FoxPro for running os commands
        WIN_OS_PAYLOADS.put("run " + WIN_TEST_CMD + NULL_BYTE_CHARACTER, WIN_CTRL_PATTERN);

        // uninitialized variable waf bypass
        insertedCMD = insertUninitVar(NIX_TEST_CMD);
        // No quote payloads
        NIX_OS_PAYLOADS.put("&" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put(";" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        // Double quote payloads
        NIX_OS_PAYLOADS.put("\"&" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("\";" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        // Single quote payloads
        NIX_OS_PAYLOADS.put("'&" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("';" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        // Special payloads
        NIX_OS_PAYLOADS.put("||" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
        NIX_OS_PAYLOADS.put("&&" + insertedCMD + NULL_BYTE_CHARACTER, NIX_CTRL_PATTERN);
    }

    // Logger instance
    private static final Logger LOGGER = LogManager.getLogger(CommandInjectionScanRule.class);

    // Get WASC Vulnerability description
    private static final Vulnerability vuln = Vulnerabilities.getVulnerability("wasc_31");

    @Override
    public int getId() {
        return 90020;
    }

    @Override
    public String getName() {
        return Constant.messages.getString(MESSAGE_PREFIX + "name");
    }

    @Override
    public boolean targets(TechSet technologies) {
        if (technologies.includes(Tech.Linux)
                || technologies.includes(Tech.MacOS)
                || technologies.includes(Tech.Windows)) {
            return true;
        }
        return false;
    }

    @Override
    public String getDescription() {
        return Constant.messages.getString(MESSAGE_PREFIX + "desc");
    }

    @Override
    public int getCategory() {
        return Category.INJECTION;
    }

    @Override
    public String getSolution() {
        if (vuln != null) {
            return vuln.getSolution();
        }

        return "Failed to load vulnerability solution from file";
    }

    @Override
    public String getReference() {
        return Constant.messages.getString(MESSAGE_PREFIX + "refs");
    }

    @Override
    public Map<String, String> getAlertTags() {
        return ALERT_TAGS;
    }

    @Override
    public int getCweId() {
        return 78;
    }

    @Override
    public int getWascId() {
        return 31;
    }

    @Override
    public int getRisk() {
        return Alert.RISK_HIGH;
    }

    private String getOtherInfo(String testType, String testValue) {
        return Constant.messages.getString(MESSAGE_PREFIX + "otherinfo." + testType, testValue);
    }

    /**
     * Scan for OS Command Injection Vulnerabilities
     *
     * @param msg a request only copy of the original message (the response isn't copied)
     * @param paramName the parameter name that need to be exploited
     * @param value the original parameter value
     */
    @Override
    public void scan(HttpMessage msg, String paramName, String value) {

        // Begin scan rule execution
        LOGGER.debug(
                "Checking [{}][{}], parameter [{}] for OS Command Injection Vulnerabilities",
                msg.getRequestHeader().getMethod(),
                msg.getRequestHeader().getURI(),
                paramName);

        // Number of targets to try
        int targetCount = 0;

        switch (this.getAttackStrength()) {
            case LOW:
                targetCount = 3;
                break;

            case MEDIUM:
                targetCount = 7;
                break;

            case HIGH:
                targetCount = 13;
                break;

            case INSANE:
                targetCount =
                        Math.max(
                                PS_PAYLOADS.size(),
                                (Math.max(NIX_OS_PAYLOADS.size(), WIN_OS_PAYLOADS.size())));
                break;

            default:
                // Default to off
        }

        if (inScope(Tech.Linux) || inScope(Tech.MacOS)) {
            if (testCommandInjection(paramName, value, targetCount, NIX_OS_PAYLOADS)) {
                return;
            }
        }

        if (isStop()) {
            return;
        }

        if (inScope(Tech.Windows)) {
            // Windows Command Prompt
            if (testCommandInjection(paramName, value, targetCount, WIN_OS_PAYLOADS)) {
                return;
            }
            // Check if the user has stopped the scan
            if (isStop()) {
                return;
            }
            // Windows PowerShell
            if (testCommandInjection(paramName, value, targetCount, PS_PAYLOADS)) {
                return;
            }
        }
    }

    /**
     * Tests for injection vulnerabilities with the given payloads.
     *
     * @param paramName the name of the parameter that will be used for testing for injection
     * @param value the value of the parameter that will be used for testing for injection
     * @param targetCount the number of requests for normal payloads
     * @param osPayloads the normal payloads
     * @return {@code true} if the vulnerability was found, {@code false} otherwise.
     */
    private boolean testCommandInjection(
            String paramName, String value, int targetCount, Map<String, Pattern> osPayloads) {
        // Start testing OS Command Injection patterns
        // ------------------------------------------
        String payload;
        String paramValue;
        Iterator<String> it = osPayloads.keySet().iterator();
        boolean firstPayload = true;

        // -----------------------------------------------
        // Check 1: Feedback based OS Command Injection
        // -----------------------------------------------
        // try execution check sending a specific payload
        // and verifying if it returns back the output inside
        // the response content
        // -----------------------------------------------
        for (int i = 0; it.hasNext() && (i < targetCount); i++) {
            payload = it.next();
            if (osPayloads.get(payload).matcher(getBaseMsg().getResponseBody().toString()).find()) {
                continue; // The original matches the detection so continue to next
            }

            HttpMessage msg = getNewMsg();
            paramValue = firstPayload ? payload : value + payload;
            firstPayload = false;
            setParameter(msg, paramName, paramValue);

            LOGGER.debug("Testing [{}] = [{}]", paramName, paramValue);

            try {
                // Send the request and retrieve the response
                try {
                    sendAndReceive(msg, false);
                } catch (SocketException ex) {
                    LOGGER.debug(
                            "Caught {} {} when accessing: {}.\n The target may have replied with a poorly formed redirect due to our input.",
                            ex.getClass().getName(),
                            ex.getMessage(),
                            msg.getRequestHeader().getURI());
                    continue; // Something went wrong, move to next payload iteration
                }

                // Check if the injected content has been evaluated and printed
                String content = msg.getResponseBody().toString();

                if (msg.getResponseHeader().hasContentType("html")) {
                    content = StringEscapeUtils.unescapeHtml4(content);
                }

                Matcher matcher = osPayloads.get(payload).matcher(content);
                if (matcher.find()) {
                    // We Found IT!
                    // First do logging
                    LOGGER.debug(
                            "[OS Command Injection Found] on parameter [{}] with value [{}]",
                            paramName,
                            paramValue);
                    String otherInfo = getOtherInfo("feedback-based", paramValue);

                    newAlert()
                            .setConfidence(Alert.CONFIDENCE_MEDIUM)
                            .setParam(paramName)
                            .setAttack(paramValue)
                            .setEvidence(matcher.group())
                            .setMessage(msg)
                            .setOtherInfo(otherInfo)
                            .raise();

                    // All done. No need to look for vulnerabilities on subsequent
                    // payloads on the same request (to reduce performance impact)
                    return true;
                }

            } catch (IOException ex) {
                // Do not try to internationalise this.. we need an error message in any event..
                // if it's in English, it's still better than not having it at all.
                LOGGER.warn(
                        "Command Injection vulnerability check failed for parameter [{}] and payload [{}] due to an I/O error",
                        paramName,
                        payload,
                        ex);
            }

            // Check if the scan has been stopped
            // if yes dispose resources and exit
            if (isStop()) {
                // Dispose all resources
                // Exit the scan rule
                return false;
            }
        }
        return false;
    }

    /**
     * Generate payload variants for uninitialized variable waf bypass
     * https://www.secjuice.com/web-application-firewall-waf-evasion/
     *
     * @param cmd the cmd to insert uninitialized variable
     */
    private static String insertUninitVar(String cmd) {
        int varLength = ThreadLocalRandom.current().nextInt(1, 3) + 1;
        char[] array = new char[varLength];
        // $xx
        array[0] = '$';
        for (int i = 1; i < varLength; ++i) {
            array[i] = (char) ThreadLocalRandom.current().nextInt(97, 123);
        }
        String var = new String(array);

        // insert variable before each space and '/' in the path
        return cmd.replaceAll("\\s", Matcher.quoteReplacement(var + " "))
                .replaceAll("\\/", Matcher.quoteReplacement(var + "/"));
    }
}
