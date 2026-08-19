export const VEHICLE_3D_MANIFEST = {
  id: 'bugatti-la-voiture-noire',
  displayName: '2022 Bugatti La Voiture Noire（非商业课程演示）',
  modelUrl: '/models/vehicles/bugatti-la-voiture-noire/BugattiLaVoitureNoire.draco.glb',
  environmentUrl: '/models/env/studio_small_09_1k.hdr',
  decoder: {
    draco: '/models/decoders/draco/',
    basis: '/models/decoders/basis/',
  },
  sourceUrl: 'https://sketchfab.com/3d-models/2022-bugatti-la-voiture-noire-b99b24064dfe40a9b358ac633a5448f2',
  authorUrl: 'https://sketchfab.com/outpiston',
  licenseUrl: 'https://creativecommons.org/licenses/by-nc-sa/4.0/',
  attribution: '“2022 Bugatti La Voiture Noire” by OUTPISTON（Sketchfab），CC BY-NC-SA 4.0；已 Draco 压缩。仅限非商业课程演示，不代表实际销售车辆，亦不代表 Bugatti 官方参与本商城。环境贴图：Poly Haven studio_small_09（CC0）。',
  framing: {
    widthRatio: 0.872,
    heightBias: 0,
    polarMin: 1.08,
    polarMax: 1.42,
    minDistanceScale: 0.82,
    maxDistanceScale: 2.4,
  },
  cameras: {
    front45: { azimuth: 0.68, polar: 1.34 },
    side: { azimuth: 1.57, polar: 1.35 },
    rear45: { azimuth: 2.46, polar: 1.34 },
    rear: { azimuth: Math.PI, polar: 1.35 },
    front: { azimuth: 0, polar: 1.33 },
    interior: { azimuth: 0.18, polar: 1.28, distanceScale: 0.34, interior: true, variant: 'driver' },
    cabinWide: { azimuth: 0.18, polar: 1.28, distanceScale: 0.34, interior: true, variant: 'cabinWide' },
  },
  wheels: {
    W19: { scale: 1, rim: '#c8ccd1', metalness: 0.82, roughness: 0.28 },
    W20: { scale: 1.045, rim: '#2d3136', metalness: 0.78, roughness: 0.32 },
    W21: { scale: 1.08, rim: '#8a7355', metalness: 0.88, roughness: 0.18 },
  },
  interiors: {
    BLACK: '#2a2a2a',
    BEIGE: '#d8c7b0',
    SPORT: '#7a1f1f',
  },
  paints: {
    // 素色白漆应按介电材质处理。较低的基色亮度会在 ACES 色调映射后
    // 保留车门、翼子板和前舱盖的明暗层次，避免整片车漆被压成纯白。
    WHITE: '#d6d1c8',
    BLACK: '#1f2328',
    SILVER: '#b8c0c8',
    GREEN: '#5e7a62',
  },
  paintFinish: {
    WHITE: {
      metalness: 0.08,
      roughness: 0.42,
      clearcoat: 0.7,
      clearcoatRoughness: 0.22,
      envMapIntensity: 0.46,
      specularIntensity: 0.48,
    },
    BLACK: {
      metalness: 0.68,
      roughness: 0.25,
      clearcoat: 0.95,
      clearcoatRoughness: 0.11,
      envMapIntensity: 0.9,
      specularIntensity: 0.72,
    },
    SILVER: {
      metalness: 0.84,
      roughness: 0.28,
      clearcoat: 0.72,
      clearcoatRoughness: 0.16,
      envMapIntensity: 0.86,
      specularIntensity: 0.8,
    },
    GREEN: {
      metalness: 0.62,
      roughness: 0.3,
      clearcoat: 0.86,
      clearcoatRoughness: 0.16,
      envMapIntensity: 0.8,
      specularIntensity: 0.76,
    },
  },
  packs: {
    TRACK: { preview: 'caliper', caliper: '#b42318' },
    WINTER: { preview: false, reason: '当前演示模型暂不支持外观预览' },
    COMFORT: { preview: false, reason: '当前演示模型暂不支持外观预览' },
  },
}

export const DEFAULT_CALIPER = '#4a4d52'
