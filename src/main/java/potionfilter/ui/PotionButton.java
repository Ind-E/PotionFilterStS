package potionfilter.ui;

import basemod.IUIElement;
import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.Hitbox;
import com.megacrit.cardcrawl.helpers.MathHelper;
import com.megacrit.cardcrawl.helpers.TipHelper;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.localization.UIStrings;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import potionfilter.util.TextureLoader;

import java.util.HashMap;

import static potionfilter.PotionFilterMod.*;

public class PotionButton implements IUIElement {
    public AbstractPotion potion;
    private float y;
    private final Hitbox hitbox;
    public boolean disabled;
    private static final Texture x_texture;
    private static final UIStrings uiStrings = CardCrawlGame.languagePack.getUIString(makeID("Warnings"));

    static {
        x_texture = TextureLoader.getTexture(imagePath("x_cross.png"));
    }


    public PotionButton(AbstractPotion potion, float x, float y, float size) {
        this.potion = potion;
        this.y = y;
        this.hitbox = new Hitbox(x, this.y, size, size);
    }

    @Override
    public void render(SpriteBatch sb) {
        sb.setColor(Color.WHITE);
        potion.labRender(sb);
        if (disabled) {
            sb.draw(x_texture, potion.posX - 32F * Settings.scale, potion.posY - 38F * Settings.scale, 64F * Settings.scale, 64F * Settings.scale);
        }
        hitbox.render(sb);
    }

    @Override
    public void update() {
        hitbox.update();
        if (hitbox.hovered) {
            if (InputHelper.justClickedLeft) {
                CardCrawlGame.sound.play("UI_CLICK_1");
                if (!disabled) {
                    HashMap<AbstractPotion.PotionRarity, Integer> enough = new HashMap<>();
                    for (IUIElement e : settingsPanel.getUIElements()) {

                        if (!(e instanceof PotionButton && !((PotionButton) e).disabled)) {
                            continue;
                        }
                        if (ReflectionHacks.getPrivate(((PotionButton) e).potion, AbstractPotion.class, "labOutlineColor") != Settings.HALF_TRANSPARENT_BLACK_COLOR) {
                            continue;
                        }
                        enough.compute(((PotionButton) e).potion.rarity, (key, value) -> (value == null) ? 1 : value + 1);
                    }
                    if (enough.get(potion.rarity) <= 1 && ReflectionHacks.getPrivate(potion, AbstractPotion.class, "labOutlineColor") == Settings.HALF_TRANSPARENT_BLACK_COLOR) {
                        warningLabel.text = uiStrings.TEXT[1];
                        warningLabel.update();
                        return;
                    }
                }
                disabled = !disabled;
                warningLabel.text = uiStrings.TEXT[0];
                warningLabel.update();
            }
            TipHelper.queuePowerTips(15.0F * Settings.xScale, 860.0F * Settings.yScale, potion.tips);
            potion.scale = 1.5F * Settings.scale;
        } else {
            potion.scale = MathHelper.scaleLerpSnap(potion.scale, 1.2F * Settings.scale);
        }
    }

    @Override
    public int renderLayer() {
        return 1;
    }

    @Override
    public int updateOrder() {
        return 1;
    }

    public void scrollY(float dY) {
        this.potion.posY += dY;
        this.y += (int) dY;
        this.hitbox.y += dY;
    }

    @Override
    public float getY() {
        return y;
    }


}