package pl.sebcel.mclivemap;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.sebcel.mclivemap.domain.Bounds;
import pl.sebcel.mclivemap.domain.PlayerData;
import pl.sebcel.mclivemap.domain.PlayerLocation;
import pl.sebcel.mclivemap.domain.RegionCoordinates;
import pl.sebcel.mclivemap.domain.WorldMap;
import pl.sebcel.mclivemap.loaders.BlockDataLoader;
import pl.sebcel.mclivemap.loaders.BlockIdsCache;
import pl.sebcel.mclivemap.loaders.ChunkLoader_1_12;
import pl.sebcel.mclivemap.loaders.ChunkLoader_1_13;
import pl.sebcel.mclivemap.loaders.PlayerLoader;
import pl.sebcel.mclivemap.loaders.RegionImageLoader;
import pl.sebcel.mclivemap.loaders.RegionLoader;
import pl.sebcel.mclivemap.render.PlayerRenderer;
import pl.sebcel.mclivemap.render.PlayerRenderer.Mode;
import pl.sebcel.mclivemap.render.SiteRenderer;
import pl.sebcel.mclivemap.render.TerrainRenderer;
import pl.sebcel.mclivemap.utils.FileUtils;

public class Program {
    
    private RegionLoader regionLoader = new RegionLoader();
    private BlockDataLoader blockDataLoader = new BlockDataLoader();
    private PlayerLoader playerLoader = new PlayerLoader();
    private TerrainRenderer terrainRenderer = new TerrainRenderer();
    private PlayerRenderer playerRenderer = new PlayerRenderer();
    private SiteRenderer siteRenderer = new SiteRenderer();
    private RegionImageLoader regionImageLoader = new RegionImageLoader();
    private BlockIdsCache blockIdsCache = new BlockIdsCache();
    private ChunkLoader_1_12 chunkLoader_1_12 = new ChunkLoader_1_12();
    private ChunkLoader_1_13 chunkLoader_1_13 = new ChunkLoader_1_13();

    public static void main(String[] args) {
        new Program().run(args);
    }

    public void run(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java -jar mclivemap.jar <path_to_world_directory> <path_to_locations_directory> <output_directory>");
            System.exit(255);
        }

        long startTime = new Date().getTime();

        String worldDirectory = args[0];
        String locationsDirectory = args[1];
        String outputDirectory = args[2];
        String cacheDirectory = "_cache";
        
        Mode pathsDrawingMode = Mode.SINGLE_PLAYER;

        verifyDirectory(worldDirectory, false);
        verifyDirectory(locationsDirectory, false);
        verifyDirectory(outputDirectory, true);
        verifyDirectory(cacheDirectory, true);

        regionImageLoader.setRegionLoader(regionLoader);
        regionImageLoader.setTerrainRenderer(terrainRenderer);
        regionImageLoader.setCacheDirectoryName(cacheDirectory);
        regionImageLoader.setCacheInvalidationTimeInMinutes(60);

        BlockData blockData = blockDataLoader.loadBlockData("vanilla_ids.json");
        chunkLoader_1_13.setBlockIdsCache(blockIdsCache);
        regionLoader.setChunkLoader_1_12(chunkLoader_1_12);
        regionLoader.setChunkLoader_1_13(chunkLoader_1_13);
        
        ColourTable colourTable = new ColourTable();
        colourTable.loadColourTable("colourTable.csv");
        terrainRenderer.setBlockData(blockData);
        terrainRenderer.setColourTable(colourTable);
        terrainRenderer.setBlockIdsCache(blockIdsCache);
        
        List<PlayerData> playersData = playerLoader.loadPlayersData(locationsDirectory);

        Set<RegionCoordinates> allRegionsToBeLoaded = calculateAllRegionsToBeLoaded(playersData);

        System.out.println("Loading terrain");
        Map<RegionCoordinates, BufferedImage> regionImages = regionImageLoader.loadRegionImages(allRegionsToBeLoaded, worldDirectory);

        System.out.println("Creating maps");
        for (PlayerData playerData : playersData) {
            System.out.println(" - Creating map for " + playerData.getName());
            PlayerLocation playerLocation = playerData.getLastLocation();
            RegionCoordinates regionCoordinates = RegionCoordinates.fromPlayerLocation(playerLocation);

            int minX = regionCoordinates.getBounds().getMinX() - 512;
            int maxX = regionCoordinates.getBounds().getMaxX() + 512;
            int minZ = regionCoordinates.getBounds().getMinZ() - 512;
            int maxZ = regionCoordinates.getBounds().getMaxZ() + 512;

            Bounds mapBounds = new Bounds(minX, minZ, maxX, maxZ);
            WorldMap worldMap = new WorldMap(mapBounds);

            List<RegionCoordinates> regionsToBeDrawn = new ArrayList<>();
            regionsToBeDrawn.add(regionCoordinates);
            regionsToBeDrawn.add(regionCoordinates.left());
            regionsToBeDrawn.add(regionCoordinates.left().up());
            regionsToBeDrawn.add(regionCoordinates.left().down());
            regionsToBeDrawn.add(regionCoordinates.right());
            regionsToBeDrawn.add(regionCoordinates.right().up());
            regionsToBeDrawn.add(regionCoordinates.right().down());
            regionsToBeDrawn.add(regionCoordinates.up());
            regionsToBeDrawn.add(regionCoordinates.down());

            System.out.println("   - Rendering terrain");
            for (RegionCoordinates regCoord : regionsToBeDrawn) {
                BufferedImage regionImage = regionImages.get(regCoord);
                worldMap.setImageFragment(regionImage, regCoord.getBounds());
            }
            System.out.println("   - Rendering players");
            playerRenderer.renderPlayers(worldMap, playerData.getName(), playersData, pathsDrawingMode);

            System.out.println("   - Rendering and saving PNG image");
            renderAndSave(worldMap, outputDirectory, playerData.getName());
        }

        System.out.println("Rendering site");
        String site = siteRenderer.renderSite(playersData, "template.html");
        FileUtils.saveFile(outputDirectory + File.separator + "livemaps.html", site);

        long endTime = new Date().getTime();
        long duration = endTime - startTime;

        System.out.println("Done. Duration: " + duration / 1000 + " seconds.");
    }

    private void verifyDirectory(String directoryName, boolean createIfDoesNotExist) {
        File directory = new File(directoryName);

        if (!directory.exists()) {
            if (createIfDoesNotExist) {
                directory.mkdirs();
                return;
            }
            System.err.println("Directory " + directory + " does not exist");
            System.exit(255);
        }
        if (!directory.isDirectory()) {
            System.out.println(directory + " is not a directory");
            System.exit(255);
        }
    }

    private Set<RegionCoordinates> calculateAllRegionsToBeLoaded(List<PlayerData> playersData) {
        Set<RegionCoordinates> allRegionsToBeLoaded = new HashSet<>();
        for (PlayerData playerData : playersData) {
            PlayerLocation playerLocation = playerData.getLastLocation();
            RegionCoordinates regionCoordinates = RegionCoordinates.fromPlayerLocation(playerLocation);
            allRegionsToBeLoaded.add(regionCoordinates);
            allRegionsToBeLoaded.add(regionCoordinates.left());
            allRegionsToBeLoaded.add(regionCoordinates.left().up());
            allRegionsToBeLoaded.add(regionCoordinates.left().down());
            allRegionsToBeLoaded.add(regionCoordinates.right());
            allRegionsToBeLoaded.add(regionCoordinates.right().up());
            allRegionsToBeLoaded.add(regionCoordinates.right().down());
            allRegionsToBeLoaded.add(regionCoordinates.up());
            allRegionsToBeLoaded.add(regionCoordinates.down());
        }

        return allRegionsToBeLoaded;
    }


    private void renderAndSave(WorldMap worldMap, String outputDirectory, String playerName) {
        new Thread(() -> {
            byte[] mapImage = worldMap.getImageAsPNG();
            String fileName = outputDirectory + File.separator + "map-" + playerName + ".png";
            FileUtils.saveFile(fileName, mapImage);
        }).start();
    }
}