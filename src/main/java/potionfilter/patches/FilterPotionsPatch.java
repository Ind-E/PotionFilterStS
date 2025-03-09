package potionfilter.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2;
import com.evacipated.cardcrawl.modthespire.lib.SpirePostfixPatch;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import potionfilter.PotionFilterMod;

import java.util.ArrayList;

@SpirePatch2(clz = PotionHelper.class, method = "getPotions")
public class FilterPotionsPatch {

    public static boolean shouldReturnAll = false;

    @SpirePostfixPatch
    public static ArrayList<String> Postfix(ArrayList<String> __result) {
        if (shouldReturnAll) {
            return __result;
        }
        ArrayList<String> toRemove = new ArrayList<>();

        for (String id: __result) {
            for (String banned: PotionFilterMod.bannedPotions) {
                if (id.equals(banned)) {
                    toRemove.add(id);
                }
            }
        }

        __result.removeAll(toRemove);
        return __result;

    }
}
