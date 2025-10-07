package com.sana.paranoiaplus;

import org.bukkit.plugin.java.JavaPlugin;

import com.sana.paranoiaplus.modules.CoreModule;
import com.sana.paranoiaplus.modules.ShadowModule;
import com.sana.paranoiaplus.modules.MobsModule;
import com.sana.paranoiaplus.modules.FakeModule;
import com.sana.paranoiaplus.modules.MLModule;

import com.sana.paranoiaplus.commands.ShadowCommand;
import com.sana.paranoiaplus.commands.FakeCommand;
import com.sana.paranoiaplus.commands.PMobCommand;
import com.sana.paranoiaplus.commands.ParanoiaCommand;

/**
 * ParanoiaPlus - main plugin entry. Improved lifecycle, configuration access and safe shutdown.
 * Stamina module removed per request.
 */
public class ParanoiaPlus extends JavaPlugin {

    private CoreModule core;
    private ShadowModule shadow;
    private MobsModule mobs;
    private FakeModule fake;
    private MLModule ml;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.core = new CoreModule(this);
        this.core.onEnable();

        this.shadow = new ShadowModule(this, core);
        this.mobs = new MobsModule(this, core);
        this.fake = new FakeModule(this, core);
        this.ml = new MLModule(this, core);

        // Module enable order: core -> ml -> shadow/mobs/fake
        ml.onEnable();
        shadow.onEnable();
        mobs.onEnable();
        fake.onEnable();

        // Register commands
        if (getCommand("shadow") != null) getCommand("shadow").setExecutor(new ShadowCommand(this, shadow));
        if (getCommand("pmob") != null) getCommand("pmob").setExecutor(new PMobCommand(this, mobs));
        if (getCommand("fake") != null) getCommand("fake").setExecutor(new FakeCommand(this, fake));
        if (getCommand("paranoia") != null) getCommand("paranoia").setExecutor(new ParanoiaCommand(this));

        getLogger().info("ParanoiaPlus enabled (enhanced)."); 
    }

    @Override
    public void onDisable() {
        if (fake != null) fake.onDisable();
        if (mobs != null) mobs.onDisable();
        if (shadow != null) shadow.onDisable();
        if (ml != null) ml.onDisable();
        if (core != null) core.onDisable();
        getLogger().info("ParanoiaPlus disabled.");
    }

    public MLModule getMlModule() { return ml; }
    public FakeModule getFakeModule() { return fake; }
    public ShadowModule getShadowModule() { return shadow; }
    public MobsModule getMobsModule() { return mobs; }
}
