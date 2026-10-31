// Install-time asset pack for the bundled models (3.2). Model files are downloaded at build
// time by T0.5 and never committed.
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("models_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
