import React, { useEffect, useRef, useMemo } from 'react';
import * as echarts from 'echarts';
import 'echarts-gl';
import './world.js'; // 引入以注册 'world' 地图数据
import './GlobeChart.css';

/**
 * 辅助函数：生成高精度地理纹理，避免外链图片跨域或加载失败导致的“白球”问题
 */
const createGlobeCanvas = () => {
  const canvas = document.createElement('canvas');
  canvas.width = 4096;
  canvas.height = 2048;
  const ctx = canvas.getContext('2d');
  if (!ctx) return canvas;

  // 1. 背景：深邃海洋
  const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height);
  gradient.addColorStop(0, '#001a33');
  gradient.addColorStop(0.5, '#000d1a');
  gradient.addColorStop(1, '#001a33');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // 2. 绘制经纬度网格线
  ctx.strokeStyle = 'rgba(77, 215, 255, 0.2)';
  ctx.lineWidth = 1;
  // 经线
  for (let i = 0; i <= 360; i += 15) {
    const x = (i / 360) * canvas.width;
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, canvas.height);
    ctx.stroke();
  }
  // 纬线
  for (let i = -90; i <= 90; i += 15) {
    const y = ((90 - i) / 180) * canvas.height;
    ctx.beginPath();
    ctx.moveTo(0, y);
    ctx.lineTo(canvas.width, y);
    ctx.stroke();
  }

  // 3. 绘制陆地轮廓 (从注册的 world 地图数据中获取)
  // 注意：echarts.getMap 是同步获取已注册的地图
  let worldData = echarts.getMap('world');
  
  if (worldData && worldData.geoJSON) {
    ctx.fillStyle = 'rgba(26, 92, 215, 0.9)'; // 陆地颜色
    ctx.strokeStyle = 'rgba(77, 215, 255, 0.5)'; // 海岸线
    ctx.lineWidth = 1;

    const features = worldData.geoJSON.features || [];
    features.forEach(feature => {
      const geometry = feature.geometry;
      if (!geometry) return;
      const coordinates = geometry.coordinates;
      const type = geometry.type;

      const drawPolygon = (coords) => {
        if (!Array.isArray(coords) || coords.length === 0) return;
        ctx.beginPath();
        coords.forEach((coord, index) => {
          const x = ((coord[0] + 180) / 360) * canvas.width;
          const y = ((90 - coord[1]) / 180) * canvas.height;
          if (index === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        });
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
      };

      if (type === 'Polygon') {
        drawPolygon(coordinates[0]);
      } else if (type === 'MultiPolygon') {
        coordinates.forEach(polygon => drawPolygon(polygon[0]));
      }
    });
  } else {
    // 如果地图还没加载好，画个提示（或者保持海洋背景）
    ctx.fillStyle = '#fff';
    ctx.font = '48px Arial';
    ctx.fillText('Loading Map Data...', 100, 100);
  }

  return canvas;
};

/**
 * GlobeChart Component
 * 封装 ECharts-GL 3D 地球与航线渲染逻辑
 * 
 * @param {Object} props
 * @param {Array} props.routes 航线数据 [[lon1, lat1], [lon2, lat2]]
 * @param {Array} props.geoPoints 业务告警点位数据
 * @param {Object} props.config 相机、光照等配置
 * @param {string} props.height 容器高度
 * @param {string} props.theme 主题色 (dark/light)
 */
const GlobeChart = ({ 
  routes = [], 
  geoPoints = [],
  config = {}, 
  height = '440px', 
  theme = 'dark',
  onReady
}) => {
  const chartRef = useRef(null);
  const chartInstance = useRef(null);
  const ROOT_PATH = 'https://echarts.apache.org/examples';
  
  // 地图数据来源说明
  // 审图号：GS(2021)648号（高德地图）
  // 数据提供方：高德软件有限公司
  const MAP_ATTRIBUTION = {
    source: '高德地图 / ECharts 官方示例数据',
    url: 'https://echarts.apache.org/examples/data/asset/geo/world.json',
    approvalNumber: 'GS(2021)648号',
    provider: '高德软件有限公司',
    note: '世界地图数据仅供安全态势可视化展示使用'
  };

  const severityWeight = { low: 12, medium: 18, high: 26, critical: 34 };
  const campusCoord = [116.4074, 39.9042];

  // 使用 useMemo 缓存纹理，避免频繁重绘
  const globeCanvas = useMemo(() => {
    return createGlobeCanvas();
  }, []); // 仅在组件挂载时生成一次

  // 默认配置
  const defaultConfig = useMemo(() => ({
    autoRotate: false,
    distance: 180,
    ambientIntensity: 0.4,
    mainIntensity: 0.4,
    ...config
  }), [config]);

  useEffect(() => {
    if (!chartRef.current) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current, theme);
    }

    const myChart = chartInstance.current;
    
    // 增加防御性检查，确保数据存在
    const safeGeoPoints = geoPoints || [];
    const safeRoutes = routes || [];

    // 对点位进行轻微偏移（Jitter），防止重叠导致点位“丢失”
    // 同时兼容多种经纬度字段名 (longitude, lng, lon, long / latitude, lat)
    const processedPoints = safeGeoPoints.map((point, index) => {
      const lon = Number(point.longitude ?? point.lng ?? point.lon ?? point.long ?? 0);
      const lat = Number(point.latitude ?? point.lat ?? 0);
      
      // 如果坐标是 [0, 0]，且不是由于原始经纬度就是 0，0（极少见）
      // 则可能代表经纬度解析失败或后端未返回有效的地理位置
      // 我们不应该直接过滤掉，否则总数对不上。我们将其映射到北京（校园）周边或特定区域
      const isMissingCoord = (lon === 0 && lat === 0);

      // 为相同位置的点增加偏移量，使其在视觉上能区分开
      // 偏移范围（度）：0.8 确保在高缩放级别下能分清
      const jitter = 0.8; 
      const offsetLon = (Math.sin(index * 123.45) * jitter);
      const offsetLat = (Math.cos(index * 67.89) * jitter);

      const finalLon = isMissingCoord ? (campusCoord[0] + offsetLon * 2) : (lon + offsetLon);
      const finalLat = isMissingCoord ? (campusCoord[1] + offsetLat * 2) : (lat + offsetLat);

      return {
        ...point,
        longitude: finalLon,
        latitude: finalLat,
        originalLon: lon,
        originalLat: lat,
        isMissingCoord
      };
    });

    const scatterData = processedPoints.map((point) => ({
      value: [point.longitude, point.latitude, 0],
      itemStyle: {
        color: point.severity === 'critical' ? '#ff3d71' : '#4dd7ff',
        opacity: 0.8
      },
      sizeWeight: severityWeight[point.severity] || 12,
      meta: point
    }));

    const attackLines = processedPoints.map((point) => ({
      coords: [
        [point.longitude, point.latitude, 0],
        [campusCoord[0], campusCoord[1], 0]
      ],
      lineStyle: {
        color: point.severity === 'critical' ? '#ff3d71' : '#4dd7ff',
        opacity: 0.8,
        width: 1.5
      },
      meta: point
    }));

    const option = {
      backgroundColor: theme === 'dark' ? 'transparent' : '#fff',
      title: {
        text: '',
        subtext: '地图审图号：GS(2021)648号 | 来源：高德地图',
        left: 'center',
        bottom: 0,
        subtextStyle: {
          color: theme === 'dark' ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.4)',
          fontSize: 10
        }
      },
      globe: {
        baseTexture: globeCanvas,
        shading: 'color', 
        environment: theme === 'dark' ? '#000' : '#fff',
        atmosphere: {
          show: true,
          glowPower: 3,
          innerGlowPower: 1.5,
          color: '#1a5cd7'
        },
        viewControl: {
          autoRotate: defaultConfig.autoRotate,
          distance: defaultConfig.distance,
          rotateMouseButton: 'left',
          panMouseButton: 'right',
          alpha: 30,
          beta: 160,
          projection: 'perspective' // 确保使用透视投影
        },
        layers: [
          {
            type: 'blend',
            texture: globeCanvas
          }
        ],
        postEffect: {
          enable: true,
          bloom: {
            enable: true,
            bloomIntensity: 0.3
          }
        }
      },
      tooltip: {
        show: true,
        backgroundColor: 'rgba(0, 20, 40, 0.9)',
        borderColor: '#4dd7ff',
        borderWidth: 1,
        textStyle: { color: '#fff' },
        formatter: (params) => {
          const meta = params.data.meta;
          if (!meta) return null;
          const loc = meta.isMissingCoord ? '位置解析失败' : (meta.city || '未知地点');
          return `
            <div style="font-weight: bold; border-bottom: 1px solid #4dd7ff; margin-bottom: 5px;">${loc}</div>
            类型: ${meta.alertType || '未知'}<br/>
            级别: <span style="color: ${meta.severity === 'critical' ? '#ff3d71' : '#4dd7ff'}">${meta.severity || '未知'}</span><br/>
            源IP: ${meta.srcIp || '未知'}
          `;
        }
      },
      series: [
        {
          type: 'scatter3D',
          coordinateSystem: 'globe',
          blendMode: 'lighter',
          symbol: 'diamond', 
          symbolSize: (value, params) => {
            const weight = params.data.sizeWeight || 12;
            return Math.sqrt(weight) * 2.5;
          },
          itemStyle: {
            opacity: 1,
            borderWidth: 0
          },
          emphasis: {
            itemStyle: {
              color: '#fff',
              opacity: 1
            }
          },
          data: scatterData
        },
        {
          type: 'lines3D',
          coordinateSystem: 'globe',
          effect: {
            show: true,
            period: 2500,
            trailWidth: 3,
            trailLength: 0.2,
            trailColor: '#ff3d71',
            trailOpacity: 1
          },
          lineStyle: {
            width: 1.5,
            curveness: 0.3
          },
          blendMode: 'lighter',
          data: attackLines
        }
        // 移除背景航线 flights，避免干扰真实的攻击数据展示
      ]
    };

    myChart.setOption(option);

    if (onReady) onReady(myChart);

    const handleResize = () => {
      myChart.resize();
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      myChart.dispose();
      chartInstance.current = null;
    };
  }, [routes, geoPoints, defaultConfig, theme, onReady]);

  return (
    <div 
      className="globe-chart-container" 
      ref={chartRef} 
      style={{ width: '100%', height }}
    />
  );
};

export default GlobeChart;
