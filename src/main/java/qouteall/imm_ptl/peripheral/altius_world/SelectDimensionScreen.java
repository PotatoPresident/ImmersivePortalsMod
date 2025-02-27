package qouteall.imm_ptl.peripheral.altius_world;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class SelectDimensionScreen extends Screen {
    public final AltiusScreen parent;
    private DimListWidget dimListWidget;
    private Button confirmButton;
    private Consumer<ResourceKey<Level>> outerCallback;
    
    protected SelectDimensionScreen(AltiusScreen parent, Consumer<ResourceKey<Level>> callback) {
        super(new TranslatableComponent("imm_ptl.select_dimension"));
        this.parent = parent;
        this.outerCallback = callback;
    }
    
    @Override
    protected void init() {
        dimListWidget = new DimListWidget(
            width,
            height,
            20,
            height - 30,
            DimEntryWidget.widgetHeight,
            this,
            DimListWidget.Type.addDimensionList
        );
        addWidget(dimListWidget);
        
        Consumer<DimEntryWidget> callback = w -> dimListWidget.setSelected(w);
    
        List<ResourceKey<Level>> dimensionList = parent.dimensionListSupplier.apply(this);
        
        for (ResourceKey<Level> dim : dimensionList) {
            dimListWidget.entryWidgets.add(new DimEntryWidget(dim, dimListWidget, callback, DimEntryWidget.Type.simple));
        }
        
        dimListWidget.update();
        
        confirmButton = (Button) addRenderableWidget(new Button(
            this.width / 2 - 75, this.height - 28, 150, 20,
            new TranslatableComponent("imm_ptl.confirm_select_dimension"),
            (buttonWidget) -> {
                DimEntryWidget selected = dimListWidget.getSelected();
                if (selected == null) {
                    return;
                }
                outerCallback.accept(selected.dimension);
                Minecraft.getInstance().setScreen(parent);
            }
        ));
        
    }
    
    @Override
    public void onClose() {
        // When `esc` is pressed return to the parent screen rather than setting screen to `null` which returns to the main menu.
        this.minecraft.setScreen(this.parent);
    }
    
    @Override
    public void render(PoseStack matrixStack, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrixStack);
        
        dimListWidget.render(matrixStack, mouseX, mouseY, delta);
        
        super.render(matrixStack, mouseX, mouseY, delta);
        
        this.drawCenteredString(
            matrixStack, this.font, this.title.getContents(), this.width / 2, 20, -1
        );
    }
}
