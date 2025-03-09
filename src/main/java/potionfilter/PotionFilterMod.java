package potionfilter;

import basemod.*;
import basemod.interfaces.EditStringsSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl.LwjglFileHandle;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.Patcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
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
        PostInitializeSubscriber,
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
    public static ModLabel warningLabel;
    public static SpireConfig modConfig;
    private static UIStrings uiStrings;
    private static final String configKey = "FilteredPotions";

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
        for (String id : modConfig.getString(configKey).split(",")) {
            bannedPotions.add(id);
        }

    }

    public PotionFilterMod() {
        BaseMod.subscribe(this);
        logger.info(modID + " subscribed to BaseMod.");

    }

    @Override
    public void receivePostInitialize() {

        uiStrings = CardCrawlGame.languagePack.getUIString(makeID("Buttons"));

        Texture badgeTexture = TextureLoader.getTexture(imagePath("badge.png"));

        settingsPanel = new ModPanel();

        potionGrid = new ArrayList<>();
        int potionSize = (int) (Settings.scale * 64F);
        int startX = (int) (Settings.WIDTH * 0.185), x = startX;
        int startY = (int) (Settings.HEIGHT * 0.694) - potionSize, y = startY;
        int rightEdge = (int) (1550 * Settings.xScale);
        int margin = 10;

        FilterPotionsPatch.shouldReturnAll = true;
        ArrayList<String> allPotions = PotionHelper.getPotions(null, true);
        FilterPotionsPatch.shouldReturnAll = false;

        for (String pot : allPotions) {
            AbstractPotion p = PotionHelper.getPotion(pot);
            p.posX = x + potionSize / 2;
            p.posY = y + potionSize / 2;
            potionGrid.add(new PotionButton(
                    p,
                    x,
                    y - (int) (6 * Settings.scale),
                    potionSize,
                    potionSize
            ));
            x += potionSize + margin;
            if (x + potionSize >= rightEdge) {
                x = startX;
                y -= potionSize + margin;
            }
        }

        for (PotionButton b : potionGrid) {
            for (String id : bannedPotions) {
                if (b.potion.ID.equals(id)) {
                    b.disabled = true;
                }
            }
            settingsPanel.addUIElement(b);
        }

        settingsPanel.addUIElement(new ModLabeledButton(uiStrings.TEXT[1], 1660, 150, settingsPanel,
                click -> {
                    for (IUIElement e : settingsPanel.getUIElements()) {
                        if (e instanceof PotionButton) {
                            ((PotionButton) e).disabled = false;
                        }
                    }
                    bannedPotions.clear();
                    warningLabel.text = "";
                    warningLabel.update();
                }));

        settingsPanel.addUIElement(new ModLabeledButton(uiStrings.TEXT[0], 1660, 225, settingsPanel,
                click -> applyChanges()));


        warningLabel = new ModLabel("", 500, 70, Color.RED.cpy(), settingsPanel, a -> {});
        settingsPanel.addUIElement(warningLabel);


        BaseMod.registerModBadge(badgeTexture, info.Name, GeneralUtils.arrToString(info.Authors), info.Description, settingsPanel);

//        registerConsoleCommands();
    }

    public static void applyChanges() {
        for (IUIElement e : settingsPanel.getUIElements()) {
            if (e instanceof PotionButton) {
                if (((PotionButton) e).disabled) {
                    bannedPotions.add(((PotionButton) e).potion.ID);
                } else {
                    bannedPotions.remove(((PotionButton) e).potion.ID);
                }
            }
        }
        warningLabel.text = "";
        warningLabel.update();

        String configString = String.join(",", bannedPotions);
        modConfig.setString(configKey, configString);
        try {
            modConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private void registerConsoleCommands() {
//        ConsoleCommand.addCommand("potionfilter", CampfireDeck.class);
//    }

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
