package won.bot.debugbot.test;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextMessageUsageCommandCheckerTest {
    @Test
    public void patternTest() {
        Pattern p = Pattern.compile("^reject(\\s+(yours))?$", Pattern.CASE_INSENSITIVE);
        check(p, "reject");
        check(p, "reject yours");
        p = Pattern.compile("^propose(\\s+((my)|(any))?\\s*([1-9])?)?$", Pattern.CASE_INSENSITIVE);
        check(p, "propose my 4");
        check(p, "propose   any  	4");
        check(p, "propose     	4");
        check(p, "propose     	");
        check(p, "propose");
        p = Pattern.compile("^retract(\\s+((mine)|(proposal)))?$");
        check(p, "retract ");
        check(p, "retract proposal");
        check(p, "retract mine ");
        check(p, "retract");
        p = Pattern.compile("wait(\\s+([0-9]{1,2}))?");
        check(p, "wait");
        check(p, "wait 5");
        check(p, "wait ");
        check(p, "wait 15 ");
    }

    private static void check(Pattern p, String text) {
        Matcher m = p.matcher(text.trim());
        System.out.println("text:" + text);
        System.out.println("pattern:" + p.toString());
        System.out.println("find:" + m.find());
        System.out.println("matches:" + m.matches());
        System.out.println("groupCount:" + m.groupCount());
        m.reset();
        if (m.find()) {
            for (int i = 0; i < m.groupCount() + 1; i++) {
                System.out.println("group " + i + ":" + m.group(i));
            }
        }
        System.out.println("----");
    }
}