package potionfilter;

import basemod.*;
import basemod.interfaces.EditStringsSubscriber;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl.LwjglFileHandle;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.AbstractPotion.PotionRarity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.scannotation.AnnotationDB;
import potionfilter.patches.FilterPotionsPatch;
import potionfilter.ui.PotionButton;
import potionfilter.util.GeneralUtils;
import potionfilter.util.TextureLoader;

import java.io.IOException;
import java.util.*;

@SpireInitializer
public class PotionFilterMod implements
        EditStringsSubscriber {
    public static ModInfo info;
    public static String modID;
    static {
        loadModInfo();
    }
    private static final String resourcesFolder = checkResourcesPath();
    public static final Logger logger = LogManager.getLogger(modID);
    public static HashSet<String> bannedPotions;
    public static ArrayList<PotionButton> potionGrid;
    public static ModPanel settingsPanel;
    public static ModLabel warningLabel, changesNotAppliedLabel;
    public static SpireConfig modConfig;
    private static final String configKey = "FilteredPotions";
    private static int potionRows;
    private static float potionSize;
    private static float potionMargin = 10F;
    private static UIStrings warningStrings;
    private static UIStrings buttonStrings;


    public static String makeID(String id) {
        return modID + ":" + id;
    }

    public static void initialize() {
        new PotionFilterMod();

        try {
            modConfig = new SpireConfig(modID, "PotionFilterSettings");
        } catch (IOException e) {
            e.printStackTrace();
        }

        bannedPotions = new HashSet<>();
        if (!modConfig.has(configKey)) {
            return;
        }
        Collections.addAll(bannedPotions, modConfig.getString(configKey).split(","));

    }

    public PotionFilterMod() {
        BaseMod.subscribe(this);
        logger.info(modID + " subscribed to BaseMod.");

    }

    public static void receivePostPostInitialize() {
        buttonStrings = CardCrawlGame.languagePack.getUIString(makeID("Buttons"));
        warningStrings = CardCrawlGame.languagePack.getUIString(makeID("Warnings"));

        Texture badgeTexture = TextureLoader.getTexture(imagePath("badge.png"));

        settingsPanel = new ModPanel();

        potionGrid = new ArrayList<>();
        potionSize = Settings.scale * 64F;
        float startX = Settings.WIDTH * 0.185F, x = startX;
        float startY = Settings.OPTION_Y + (280F * Settings.scale) - potionSize, y = startY;
        float rightEdge = 1550F * Settings.xScale;

        FilterPotionsPatch.shouldReturnAll = true;
        ArrayList<String> allPotions = PotionHelper.getPotions(null, true);
        FilterPotionsPatch.shouldReturnAll = false;

        Map<PotionRarity, Integer> potionOrder = new HashMap<>();
        potionOrder.put(PotionRarity.COMMON, 0);
        potionOrder.put(PotionRarity.UNCOMMON, 1);
        potionOrder.put(PotionRarity.RARE, 2);
        potionOrder.put(PotionRarity.PLACEHOLDER, 3);

        allPotions.sort(Comparator.comparingInt(id -> potionOrder.getOrDefault(PotionHelper.getPotion(id).rarity, Integer.MAX_VALUE)));

        potionRows = 0;
        AbstractPotion prevPotion = null;
        for (String pot : allPotions) {
            AbstractPotion p = PotionHelper.getPotion(pot);
            if (prevPotion != null && p.rarity != prevPotion.rarity) {
                x = startX;
                y -= potionSize + potionMargin;
                potionRows++;
            }
            p.posX = x + potionSize / 2;
            p.posY = y + potionSize / 2;
            potionGrid.add(new PotionButton(
                    p,
                    x,
                    y - (int) (6 * Settings.scale),
                    potionSize
            ));
            x += potionSize + potionMargin;
            if (x + potionSize >= rightEdge) {
                x = startX;
                y -= potionSize + potionMargin;
                potionRows++;
            }
            prevPotion = p;
        }

        for (PotionButton b : potionGrid) {
            for (String id : bannedPotions) {
                if (b.potion.ID.equals(id)) {
                    b.disabled = true;
                    break;
                }
            }
            settingsPanel.addUIElement(b);
        }

        // reset
        settingsPanel.addUIElement(new ModLabeledButton(buttonStrings.TEXT[1], 1660, 225, settingsPanel,
                click -> {
                    for (IUIElement e : settingsPanel.getUIElements()) {
                        if (e instanceof PotionButton) {
                            ((PotionButton) e).disabled = false;
                        }
                    }
                    applyChanges();
                }));

        // invert
        settingsPanel.addUIElement(new ModLabeledButton(buttonStrings.TEXT[2], 1660, 300, settingsPanel,
                click -> {
                    for (IUIElement e : settingsPanel.getUIElements()) {
                        if (e instanceof PotionButton) {
                            ((PotionButton) e).disabled = !((PotionButton) e).disabled ;
                        }
                    }
                    applyChanges();
                }));

        // apply
        settingsPanel.addUIElement(new ModLabeledButton(buttonStrings.TEXT[0], 1660, 150, settingsPanel,
                click -> applyChanges()));

        warningLabel = new ModLabel("", 450, 70, Color.RED.cpy(), settingsPanel, a -> {});
        settingsPanel.addUIElement(warningLabel);

        changesNotAppliedLabel = new ModLabel("", 450, 30, Color.RED.cpy(), settingsPanel, a -> {});
        settingsPanel.addUIElement(changesNotAppliedLabel);

        settingsPanel.addUIElement(new ScrollHack());


        BaseMod.registerModBadge(badgeTexture, info.Name, GeneralUtils.arrToString(info.Authors), info.Description, settingsPanel);
    }

    public static class ScrollHack implements IUIElement {
        static final float minScrollY = Settings.OPTION_Y + (280F * Settings.scale) + potionSize;
        static final float maxScrollY = minScrollY + Math.max(0, potionRows - 6) * (potionSize + potionMargin);
        static float scrollY = minScrollY;

        @Override
        public void render(SpriteBatch spriteBatch) {
        }

        @Override
        public void update() {

            float dY;
            if (InputHelper.scrolledDown) {
                dY = potionSize + potionMargin;
                if (scrollY + dY > maxScrollY) {
                    return;
                }

            } else if (InputHelper.scrolledUp) {
                dY = -(potionSize + potionMargin);
                if (scrollY <= minScrollY) {
                    return;
                }
            } else {
                return;
            }
            for (IUIElement e : settingsPanel.getUIElements()) {
                if (e instanceof PotionButton) {
                    ((PotionButton) e).scrollY(dY);
                }
            }
            scrollY += dY;
        }

        @Override
        public int renderLayer() {
            return 0;
        }

        @Override
        public int updateOrder() {
            return 0;
        }
    }

    public static void applyChanges() {
        HashMap<PotionRarity, Integer> enough = new HashMap<>();
        for (IUIElement e : settingsPanel.getUIElements()) {

            if (!(e instanceof PotionButton && !((PotionButton) e).disabled)) {
                continue;
            }
            enough.compute(((PotionButton) e).potion.rarity, (key, value) -> (value == null) ? 1 : value + 1);
        }
        for (PotionRarity rarity : PotionRarity.values()) {
            if (rarity == PotionRarity.PLACEHOLDER) {
                continue;
            }
            if (enough.getOrDefault(rarity, 0) < 1) {
                warningLabel.text = warningStrings.TEXT[1];
                changesNotAppliedLabel.text = warningStrings.TEXT[0];
                return;
            }
        }

        warningLabel.text = "";
        changesNotAppliedLabel.text = "";

        for (IUIElement e : settingsPanel.getUIElements()) {
            if (e instanceof PotionButton) {
                if (((PotionButton) e).disabled) {
                    bannedPotions.add(((PotionButton) e).potion.ID);
                } else {
                    bannedPotions.remove(((PotionButton) e).potion.ID);
                }
            }
        }

        String configString = String.join(",", bannedPotions);
        modConfig.setString(configKey, configString);
        try {
            modConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*----------Localization----------*/

    private static String getLangString() {
        return Settings.language.name().toLowerCase();
    }

    private static final String defaultLanguage = "eng";

    @Override
    public void receiveEditStrings() {
        /*
         * First, load the default localization.
         * Then, if the current language is different, attempt to load localization for
         * that language.
         * This results in the default localization being used for anything that might
         * be missing.
         * The same process is used to load keywords slightly below.
         */
        loadLocalization(defaultLanguage);
        if (!defaultLanguage.equals(getLangString())) {
            try {
                loadLocalization(getLangString());
            } catch (GdxRuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadLocalization(String lang) {
        BaseMod.loadCustomStringsFile(UIStrings.class,
                localizationPath(lang, "UIStrings.json"));
    }


    public static String localizationPath(String lang, String file) {
        return resourcesFolder + "/localization/" + lang + "/" + file;
    }

    public static String imagePath(String file) {
        return resourcesFolder + "/images/" + file;
    }

    /**
     * Checks the expected resources path based on the package name.
     */
    private static String checkResourcesPath() {
        String name = PotionFilterMod.class.getName();
        int separator = name.indexOf('.');
        if (separator > 0)
            name = name.substring(0, separator);

        FileHandle resources = new LwjglFileHandle(name, Files.FileType.Internal);

        if (!resources.exists()) {
            throw new RuntimeException("\n\tFailed to find resources folder; expected it to be named \"" + name + "\"."
                    +
                    " Either make sure the folder under resources has the same name as your mod's package, or change the line\n"
                    +
                    "\t\"private static final String resourcesFolder = checkResourcesPath();\"\n" +
                    "\tat the top of the " + PotionFilterMod.class.getSimpleName() + " java file.");
        }
        if (!resources.child("images").exists()) {
            throw new RuntimeException("\n\tFailed to find the 'images' folder in the mod's 'resources/" + name
                    + "' folder; Make sure the " +
                    "images folder is in the correct location.");
        }
        if (!resources.child("localization").exists()) {
            throw new RuntimeException("\n\tFailed to find the 'localization' folder in the mod's 'resources/" + name
                    + "' folder; Make sure the " +
                    "localization folder is in the correct location.");
        }

        return name;
    }

    /**
     * This determines the mod's ID based on information stored by ModTheSpire.
     */
    private static void loadModInfo() {
        Optional<ModInfo> infos = Arrays.stream(Loader.MODINFOS).filter((modInfo) -> {
            AnnotationDB annotationDB = Patcher.annotationDBMap.get(modInfo.jarURL);
            if (annotationDB == null)
                return false;
            Set<String> initializers = annotationDB.getAnnotationIndex().getOrDefault(SpireInitializer.class.getName(),
                    Collections.emptySet());
            return initializers.contains(PotionFilterMod.class.getName());
        }).findFirst();
        if (infos.isPresent()) {
            info = infos.get();
            modID = info.ID;
        } else {
            throw new RuntimeException("Failed to determine mod info/ID based on initializer.");
        }
    }

}
