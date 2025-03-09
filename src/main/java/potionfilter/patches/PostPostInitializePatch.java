package potionfilter.patches;

import basemod.BaseMod;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import potionfilter.PotionFilterMod;

import java.util.ArrayList;

@SpirePatch2(clz = BaseMod.class, method = "publishPostInitialize")
public class PostPostInitializePatch {

    @SpireInsertPatch(locator = Locator.class)
    public static void Insert() {
        PotionFilterMod.receivePostPostInitialize();
    }

    public static class Locator extends SpireInsertLocator {
        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher finalMatcher = new Matcher.MethodCallMatcher(BaseMod.class, "unsubscribeLaterHelper");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList<>(), finalMatcher);
        }
    }
}
