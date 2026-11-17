use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Color {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

impl Color {
    pub const WHITE: Self = Self {
        r: 1.0,
        g: 1.0,
        b: 1.0,
        a: 1.0,
    };
    pub const BLACK: Self = Self {
        r: 0.0,
        g: 0.0,
        b: 0.0,
        a: 1.0,
    };
    pub const BLUE: Self = Self {
        r: 0.133,
        g: 0.380,
        b: 0.851,
        a: 1.0,
    };
    pub const MINT: Self = Self {
        r: 0.176,
        g: 0.831,
        b: 0.749,
        a: 1.0,
    };
    pub const EMERALD: Self = Self {
        r: 0.063,
        g: 0.725,
        b: 0.506,
        a: 1.0,
    };
    pub const ROSE: Self = Self {
        r: 0.957,
        g: 0.247,
        b: 0.369,
        a: 1.0,
    };
    pub const AMBER: Self = Self {
        r: 0.961,
        g: 0.620,
        b: 0.043,
        a: 1.0,
    };
    pub const INDIGO: Self = Self {
        r: 0.506,
        g: 0.549,
        b: 0.973,
        a: 1.0,
    };

    pub const fn new(r: f32, g: f32, b: f32, a: f32) -> Self {
        Self { r, g, b, a }
    }

    pub fn from_argb_hex(hex: u32) -> Self {
        let a = ((hex >> 24) & 0xFF) as f32 / 255.0;
        let r = ((hex >> 16) & 0xFF) as f32 / 255.0;
        let g = ((hex >> 8) & 0xFF) as f32 / 255.0;
        let b = (hex & 0xFF) as f32 / 255.0;
        Self { r, g, b, a }
    }

    pub fn to_argb_hex(&self) -> u32 {
        let a = (self.a.clamp(0.0, 1.0) * 255.0).round() as u32;
        let r = (self.r.clamp(0.0, 1.0) * 255.0).round() as u32;
        let g = (self.g.clamp(0.0, 1.0) * 255.0).round() as u32;
        let b = (self.b.clamp(0.0, 1.0) * 255.0).round() as u32;
        (a << 24) | (r << 16) | (g << 8) | b
    }

    pub fn lerp(self, other: Self, t: f32) -> Self {
        let k = if t.is_finite() { t.clamp(0.0, 1.0) } else { 0.0 };
        Self {
            r: self.r + (other.r - self.r) * k,
            g: self.g + (other.g - self.g) * k,
            b: self.b + (other.b - self.b) * k,
            a: self.a + (other.a - self.a) * k,
        }
    }

    pub fn relative_luminance(&self) -> f32 {
        fn channel_lin(c: f32) -> f32 {
            let v = c.clamp(0.0, 1.0);
            if v <= 0.04045 {
                v / 12.92
            } else {
                ((v + 0.055) / 1.055).powf(2.4)
            }
        }
        0.2126 * channel_lin(self.r) + 0.7152 * channel_lin(self.g) + 0.0722 * channel_lin(self.b)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Rect {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

impl Rect {
    pub const fn new(x: f32, y: f32, width: f32, height: f32) -> Self {
        Self {
            x,
            y,
            width,
            height,
        }
    }

    pub fn sanitize(self, fallback: Self) -> Self {
        let x = if self.x.is_finite() { self.x } else { fallback.x };
        let y = if self.y.is_finite() { self.y } else { fallback.y };
        let width = if self.width.is_finite() && self.width >= 0.0 {
            self.width
        } else {
            fallback.width
        };
        let height = if self.height.is_finite() && self.height >= 0.0 {
            self.height
        } else {
            fallback.height
        };
        Self {
            x,
            y,
            width,
            height,
        }
    }

    pub fn center(&self) -> (f32, f32) {
        (self.x + self.width * 0.5, self.y + self.height * 0.5)
    }

    pub fn contains(&self, px: f32, py: f32) -> bool {
        px >= self.x && px <= self.x + self.width && py >= self.y && py <= self.y + self.height
    }

    pub fn union(&self, other: &Self) -> Self {
        let min_x = self.x.min(other.x);
        let min_y = self.y.min(other.y);
        let max_x = (self.x + self.width).max(other.x + other.width);
        let max_y = (self.y + self.height).max(other.y + other.height);
        Self {
            x: min_x,
            y: min_y,
            width: (max_x - min_x).max(0.0),
            height: (max_y - min_y).max(0.0),
        }
    }

    pub fn edge_gap(&self, other: &Self) -> f32 {
        let (cx1, cy1) = self.center();
        let (cx2, cy2) = other.center();
        let dx = (cx1 - cx2).abs() - (self.width + other.width) * 0.5;
        let dy = (cy1 - cy2).abs() - (self.height + other.height) * 0.5;
        let ox = dx.max(0.0);
        let oy = dy.max(0.0);
        (ox * ox + oy * oy).sqrt() + dx.max(dy).min(0.0)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub enum Shape {
    Rounded(f32),
    Circular,
    Smooth { radius: f32, smoothing: f32 },
}

impl Shape {
    pub fn effective_radius(&self, width: f32, height: f32) -> f32 {
        let max_r = 0.5 * width.min(height).max(0.0);
        match *self {
            Shape::Rounded(r) => r.clamp(0.0, max_r),
            Shape::Circular => max_r,
            Shape::Smooth { radius, .. } => radius.clamp(0.0, max_r),
        }
    }

    pub fn smoothing_factor(&self) -> f32 {
        match *self {
            Shape::Rounded(_) => 0.60,
            Shape::Circular => 0.0,
            Shape::Smooth { smoothing, .. } => smoothing.clamp(0.0, 1.0),
        }
    }

    pub fn superellipse_exponent(&self) -> f32 {
        let s = self.smoothing_factor();
        2.0 + s * 2.24
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Material {
    pub preset: String,
    pub refraction: f32,
    pub depth: f32,
    pub dispersion: f32,
    pub frost: f32,
    pub splay: f32,
    pub specular: f32,
    pub lighten: f32,
    pub darken: f32,
    pub luminance_mix: f32,
    pub linear_dodge: f32,
    pub color_burn: f32,
    pub saturation_keep: f32,
    pub tint: Color,
}

impl Material {
    pub fn regular() -> Self {
        Self {
            preset: "regular".to_string(),
            refraction: 56.0,
            depth: 24.0,
            dispersion: 14.0,
            frost: 14.0,
            splay: 16.0,
            specular: 0.45,
            lighten: 0.20,
            darken: 0.25,
            luminance_mix: 0.20,
            linear_dodge: 0.16,
            color_burn: 0.12,
            saturation_keep: 0.82,
            tint: Color::new(1.0, 1.0, 1.0, 0.0),
        }
    }

    pub fn clear() -> Self {
        Self {
            preset: "clear".to_string(),
            refraction: 70.0,
            depth: 30.0,
            dispersion: 20.0,
            frost: 6.0,
            splay: 20.0,
            specular: 0.40,
            lighten: 0.10,
            darken: 0.12,
            luminance_mix: 0.08,
            linear_dodge: 0.20,
            color_burn: 0.08,
            saturation_keep: 0.92,
            tint: Color::new(1.0, 1.0, 1.0, 0.0),
        }
    }

    pub fn clear_bar() -> Self {
        Self {
            preset: "clear_bar".to_string(),
            refraction: 64.0,
            depth: 26.0,
            dispersion: 18.0,
            frost: 3.0,
            splay: 18.0,
            specular: 0.44,
            lighten: 0.14,
            darken: 0.22,
            luminance_mix: 0.33,
            linear_dodge: 0.18,
            color_burn: 0.10,
            saturation_keep: 0.86,
            tint: Color::new(0.60, 0.60, 0.60, 0.33),
        }
    }

    pub fn tinted(color: Color) -> Self {
        let mut base = Self::regular();
        base.preset = "tinted".to_string();
        base.tint = Color::new(color.r, color.g, color.b, color.a.clamp(0.28, 0.82));
        base.saturation_keep = 0.64;
        base
    }

    pub fn identity() -> Self {
        Self {
            preset: "identity".to_string(),
            refraction: 0.0,
            depth: 0.0,
            dispersion: 0.0,
            frost: 0.0,
            splay: 0.0,
            specular: 0.0,
            lighten: 0.0,
            darken: 0.0,
            luminance_mix: 0.0,
            linear_dodge: 0.0,
            color_burn: 0.0,
            saturation_keep: 1.0,
            tint: Color::new(1.0, 1.0, 1.0, 0.0),
        }
    }

    pub fn from_preset_name(preset: &str, tint_argb: u32) -> Self {
        match preset.trim().to_ascii_lowercase().as_str() {
            "clear" => Self::clear(),
            "clear_bar" | "clearbar" | "dock" => Self::clear_bar(),
            "tinted" => Self::tinted(Color::from_argb_hex(tint_argb)),
            "identity" => Self::identity(),
            _ => Self::regular(),
        }
    }

    pub fn blend(&self, other: &Self, t: f32) -> Self {
        let k = if t.is_finite() { t.clamp(0.0, 1.0) } else { 0.0 };
        let lerp_f = |a: f32, b: f32| a + (b - a) * k;
        Self {
            preset: if k < 0.5 {
                self.preset.clone()
            } else {
                other.preset.clone()
            },
            refraction: lerp_f(self.refraction, other.refraction),
            depth: lerp_f(self.depth, other.depth),
            dispersion: lerp_f(self.dispersion, other.dispersion),
            frost: lerp_f(self.frost, other.frost),
            splay: lerp_f(self.splay, other.splay),
            specular: lerp_f(self.specular, other.specular),
            lighten: lerp_f(self.lighten, other.lighten),
            darken: lerp_f(self.darken, other.darken),
            luminance_mix: lerp_f(self.luminance_mix, other.luminance_mix),
            linear_dodge: lerp_f(self.linear_dodge, other.linear_dodge),
            color_burn: lerp_f(self.color_burn, other.color_burn),
            saturation_keep: lerp_f(self.saturation_keep, other.saturation_keep),
            tint: self.tint.lerp(other.tint, k),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Spring {
    pub duration: f32,
    pub bounce: f32,
    pub launched: bool,
}

impl Spring {
    pub const EMERGE: Self = Self {
        duration: 0.38,
        bounce: 0.18,
        launched: true,
    };

    pub const RECEDE: Self = Self {
        duration: 0.28,
        bounce: 0.0,
        launched: true,
    };

    pub const fn new(duration: f32, bounce: f32) -> Self {
        let clamped_b = if bounce > 0.95 {
            0.95
        } else if bounce < -0.95 {
            -0.95
        } else {
            bounce
        };
        let clamped_d = if duration < 0.01 { 0.01 } else { duration };
        Self {
            duration: clamped_d,
            bounce: clamped_b,
            launched: false,
        }
    }

    pub fn step_scalar(
        &self,
        current: f32,
        velocity: f32,
        target: f32,
        dt: f32,
        reduce_motion: bool,
    ) -> (f32, f32, bool) {
        if !current.is_finite() || !target.is_finite() {
            return (target, 0.0, false);
        }
        if !dt.is_finite() || dt <= 0.0 {
            return (current, velocity, (target - current).abs() > 0.05 || velocity.abs() > 0.2);
        }
        let dt_clamped = dt.min(0.1);
        let dur = self.duration.max(0.016);
        let bounce = if reduce_motion {
            0.0
        } else {
            self.bounce.clamp(-0.95, 0.95)
        };

        let omega = (2.0 * std::f32::consts::PI) / dur;
        let zeta = (1.0 - bounce).clamp(0.05, 1.95);

        let disp = current - target;
        let mut v0 = if velocity.is_finite() { velocity } else { 0.0 };
        if self.launched && v0.abs() < 1e-3 && disp.abs() > 0.5 {
            v0 = -disp * (omega * 1.15);
        }

        let (next_disp, next_vel) = if (zeta - 1.0).abs() < 1e-3 {
            let e = (-omega * dt_clamped).exp();
            let c1 = disp;
            let c2 = v0 + omega * disp;
            let x = (c1 + c2 * dt_clamped) * e;
            let v = (c2 - omega * (c1 + c2 * dt_clamped)) * e;
            (x, v)
        } else if zeta < 1.0 {
            let omega_d = omega * (1.0 - zeta * zeta).sqrt();
            let e = (-zeta * omega * dt_clamped).exp();
            let cos_t = (omega_d * dt_clamped).cos();
            let sin_t = (omega_d * dt_clamped).sin();
            let c1 = disp;
            let c2 = (v0 + zeta * omega * disp) / omega_d;
            let x = e * (c1 * cos_t + c2 * sin_t);
            let v = e
                * ((c2 * omega_d - c1 * zeta * omega) * cos_t
                    - (c1 * omega_d + c2 * zeta * omega) * sin_t);
            (x, v)
        } else {
            let s = (zeta * zeta - 1.0).sqrt();
            let r1 = -omega * (zeta - s);
            let r2 = -omega * (zeta + s);
            let denom = (r1 - r2).max(1e-4);
            let c1 = (v0 - r2 * disp) / denom;
            let c2 = disp - c1;
            let e1 = (r1 * dt_clamped).exp();
            let e2 = (r2 * dt_clamped).exp();
            let x = c1 * e1 + c2 * e2;
            let v = c1 * r1 * e1 + c2 * r2 * e2;
            (x, v)
        };

        let next_pos = target + next_disp;
        let still_moving = next_disp.abs() > 0.08 || next_vel.abs() > 0.35;
        if still_moving {
            (next_pos, next_vel, true)
        } else {
            (target, 0.0, false)
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Appearance {
    pub transparency: f32,
    pub dark_mode: bool,
    pub reduce_transparency: bool,
    pub increase_contrast: bool,
    pub reduce_motion: bool,
    pub adapt_to_content: bool,
    pub light_angle_rad: f32,
}

impl Default for Appearance {
    fn default() -> Self {
        Self {
            transparency: 0.5,
            dark_mode: true,
            reduce_transparency: false,
            increase_contrast: false,
            reduce_motion: false,
            adapt_to_content: true,
            light_angle_rad: -0.7853982,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ContainerId(pub usize);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct GlassId(pub usize);

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Glass {
    pub rect: Rect,
    pub shape: Shape,
    pub material: Material,
    pub container: Option<ContainerId>,
    pub z: i32,
    pub interactive: bool,
}

impl Glass {
    pub fn new(rect: Rect) -> Self {
        Self {
            rect,
            shape: Shape::Rounded(22.0),
            material: Material::regular(),
            container: None,
            z: 0,
            interactive: false,
        }
    }

    pub fn shape(mut self, shape: Shape) -> Self {
        self.shape = shape;
        self
    }

    pub fn material(mut self, material: Material) -> Self {
        self.material = material;
        self
    }

    pub fn container(mut self, container: ContainerId) -> Self {
        self.container = Some(container);
        self
    }

    pub fn z(mut self, z: i32) -> Self {
        self.z = z;
        self
    }

    pub fn interactive(mut self) -> Self {
        self.interactive = true;
        self
    }
}

#[derive(Debug, Clone)]
struct ContainerNode {
    id: ContainerId,
    spacing: f32,
    z: i32,
}

#[derive(Debug, Clone)]
struct GlassNode {
    id: GlassId,
    current_rect: Rect,
    target_rect: Rect,
    vel_x: f32,
    vel_y: f32,
    vel_w: f32,
    vel_h: f32,
    shape: Shape,
    material: Material,
    target_material: Material,
    material_blend: f32,
    material_vel: f32,
    container: Option<ContainerId>,
    z: i32,
    interactive: bool,
    spring: Spring,
    press_glow: f32,
    press_vel: f32,
    shrink_target: Option<GlassId>,
    alive: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ResolvedGlassSurface {
    pub preset: String,
    pub is_dark_face: bool,
    pub face_color_argb: u32,
    pub face_opacity: f32,
    pub tint_color_argb: u32,
    pub tint_weight: f32,
    pub rim_light_argb: u32,
    pub rim_shadow_argb: u32,
    pub hairline_argb: u32,
    pub refraction_px: f32,
    pub depth_px: f32,
    pub dispersion_px: f32,
    pub frost_radius_px: f32,
    pub splay_px: f32,
    pub specular_intensity: f32,
    pub corner_radius_px: f32,
    pub corner_smoothing: f32,
    pub superellipse_n: f32,
    pub saturation_boost: f32,
    pub wcag_contrast_ratio: f32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LiquidNeckBridge {
    pub a_id: usize,
    pub b_id: usize,
    pub gap_pt: f32,
    pub fuse_factor: f32,
    pub neck_center_x: f32,
    pub neck_center_y: f32,
    pub neck_thickness_pt: f32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LiquidGlassItemSnapshot {
    pub id: usize,
    pub rect: Rect,
    pub z: i32,
    pub press_glow: f32,
    pub surface: ResolvedGlassSurface,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LiquidGlassFramePacket {
    pub animating: bool,
    pub glass_on_glass: bool,
    pub items: Vec<LiquidGlassItemSnapshot>,
    pub liquid_bridges: Vec<LiquidNeckBridge>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct NavBarLiquidState {
    pub lens_x_fraction: f32,
    pub lens_width_fraction: f32,
    pub lens_scale_y: f32,
    pub liquid_neck_strength: f32,
    pub neighbor_pull_offset: f32,
    pub press_glow: f32,
    pub bar_surface: ResolvedGlassSurface,
    pub lens_surface: ResolvedGlassSurface,
}

pub fn resolve_surface_material(
    material: &Material,
    shape: Shape,
    width_pt: f32,
    height_pt: f32,
    appearance: &Appearance,
    backdrop_luma: f32,
) -> ResolvedGlassSurface {
    let min_dim = width_pt.min(height_pt).max(1.0);
    let max_dim = width_pt.max(height_pt).max(1.0);
    let is_large_panel = max_dim >= 180.0 || min_dim >= 120.0;
    let thickness = ((min_dim - 36.0) / 204.0).clamp(0.0, 1.0);

    let is_dark_face = if !appearance.adapt_to_content || is_large_panel {
        appearance.dark_mode
    } else {
        backdrop_luma < 0.52
    };

    let t_slider = if appearance.reduce_transparency {
        1.0
    } else {
        appearance.transparency.clamp(0.0, 1.0)
    };

    let tinted_half = ((t_slider - 0.5) * 2.0).clamp(0.0, 1.0);
    let clear_half = ((0.5 - t_slider) * 2.0).clamp(0.0, 1.0);

    let base_darken = if is_large_panel {
        0.52 + 0.08 * thickness
    } else {
        material.darken.max(0.25)
    };
    let target_darken = if is_large_panel { 0.81 } else { 0.70 };
    let resolved_darken = (base_darken * (1.0 - 0.55 * clear_half))
        + (target_darken - base_darken) * tinted_half;

    let base_lighten = if is_large_panel {
        0.55 + 0.20 * thickness
    } else {
        material.lighten.max(0.20)
    };
    let target_lighten = if is_large_panel { 0.88 } else { 0.68 };
    let resolved_lighten = (base_lighten * (1.0 - 0.55 * clear_half))
        + (target_lighten - base_lighten) * tinted_half;

    let (face_rgb, mut face_opacity) = if material.preset == "identity" {
        (Color::new(1.0, 1.0, 1.0, 0.0), 0.0)
    } else if is_dark_face {
        (
            Color::new(0.110, 0.110, 0.118, resolved_darken),
            resolved_darken.clamp(0.10, 0.88),
        )
    } else {
        (
            Color::new(0.985, 0.992, 1.0, resolved_lighten),
            resolved_lighten.clamp(0.12, 0.90),
        )
    };

    if appearance.reduce_transparency {
        face_opacity = face_opacity.max(0.84);
    }

    let frost_scale = if appearance.reduce_transparency {
        1.65
    } else {
        1.0 + 0.41421356 * tinted_half - 0.35 * clear_half
    };
    let frost_radius_px = (material.frost * (1.0 + 0.25 * thickness) * frost_scale).max(0.0);

    let contrast_boost = if appearance.increase_contrast { 1.35 } else { 1.0 };
    let specular_intensity = (material.specular * contrast_boost).clamp(0.0, 1.0);

    let rim_light = if is_dark_face {
        Color::new(0.88, 0.96, 1.0, (0.42 * contrast_boost).clamp(0.0, 0.95))
    } else {
        Color::new(1.0, 1.0, 1.0, (0.68 * contrast_boost).clamp(0.0, 0.98))
    };

    let rim_shadow = if is_dark_face {
        Color::new(0.157, 0.157, 0.157, 0.44 * contrast_boost)
    } else {
        Color::new(0.902, 0.902, 0.902, 0.38 * contrast_boost)
    };

    let hairline = if is_dark_face {
        Color::new(1.0, 1.0, 1.0, (0.18 * contrast_boost).clamp(0.08, 0.45))
    } else {
        Color::new(0.08, 0.12, 0.14, (0.14 * contrast_boost).clamp(0.06, 0.38))
    };

    let corner_radius_px = shape.effective_radius(width_pt, height_pt);
    let corner_smoothing = shape.smoothing_factor();
    let superellipse_n = shape.superellipse_exponent();

    let saturation_boost = if is_dark_face {
        material.saturation_keep.max(0.40) + 0.24 * (1.0 - tinted_half)
    } else {
        material.saturation_keep.max(0.55) + 0.18 * (1.0 - tinted_half)
    };

    let panel_luma = face_rgb.relative_luminance() * face_opacity
        + backdrop_luma.clamp(0.0, 1.0) * (1.0 - face_opacity);
    let fg_luma = if is_dark_face { 0.95 } else { 0.04 };
    let l_max = panel_luma.max(fg_luma);
    let l_min = panel_luma.min(fg_luma);
    let wcag_contrast_ratio = (l_max + 0.05) / (l_min + 0.05);

    ResolvedGlassSurface {
        preset: material.preset.clone(),
        is_dark_face,
        face_color_argb: Color::new(face_rgb.r, face_rgb.g, face_rgb.b, face_opacity).to_argb_hex(),
        face_opacity,
        tint_color_argb: material.tint.to_argb_hex(),
        tint_weight: material.tint.a.clamp(0.0, 0.85),
        rim_light_argb: rim_light.to_argb_hex(),
        rim_shadow_argb: rim_shadow.to_argb_hex(),
        hairline_argb: hairline.to_argb_hex(),
        refraction_px: material.refraction,
        depth_px: material.depth,
        dispersion_px: material.dispersion,
        frost_radius_px,
        splay_px: material.splay.max(4.0),
        specular_intensity,
        corner_radius_px,
        corner_smoothing,
        superellipse_n,
        saturation_boost,
        wcag_contrast_ratio,
    }
}

pub fn smin_polynomial(a: f32, b: f32, k: f32) -> (f32, f32) {
    if k <= 1e-4 {
        return (a.min(b), if a < b { 0.0 } else { 1.0 });
    }
    let h = (0.5 + 0.5 * (b - a) / k).clamp(0.0, 1.0);
    let d = b * (1.0 - h) + a * h - k * h * (1.0 - h);
    (d, 1.0 - h)
}

pub fn eval_squircle_sdf(px: f32, py: f32, rect: Rect, shape: Shape) -> f32 {
    let (cx, cy) = rect.center();
    let hx = (rect.width * 0.5).max(0.5);
    let hy = (rect.height * 0.5).max(0.5);
    let r = shape.effective_radius(rect.width, rect.height);
    let n = shape.superellipse_exponent();

    let qx = (px - cx).abs() - (hx - r);
    let qy = (py - cy).abs() - (hy - r);
    let ox = qx.max(0.0);
    let oy = qy.max(0.0);

    let corner_dist = if ox > 0.0 && oy > 0.0 && r > 0.0 {
        ((ox / r).powf(n) + (oy / r).powf(n)).powf(1.0 / n) * r
    } else {
        (ox * ox + oy * oy).sqrt()
    };

    corner_dist + qx.max(qy).min(0.0) - r
}

#[derive(Debug, Clone)]
pub struct Scene {
    pub physics: bool,
    pub glass_on_glass: bool,
    pub appearance: Appearance,
    containers: Vec<ContainerNode>,
    glasses: Vec<GlassNode>,
    pointer_pos: Option<(f32, f32)>,
    pointer_vel: (f32, f32),
}

impl Default for Scene {
    fn default() -> Self {
        Self::new()
    }
}

impl Scene {
    pub fn new() -> Self {
        Self {
            physics: true,
            glass_on_glass: true,
            appearance: Appearance::default(),
            containers: Vec::new(),
            glasses: Vec::new(),
            pointer_pos: None,
            pointer_vel: (0.0, 0.0),
        }
    }

    pub fn add_container(&mut self, spacing: f32, z: i32) -> ContainerId {
        let id = ContainerId(self.containers.len());
        self.containers.push(ContainerNode {
            id,
            spacing: if spacing.is_finite() {
                spacing.max(0.0)
            } else {
                0.0
            },
            z,
        });
        id
    }

    pub fn set_container_spacing(&mut self, id: ContainerId, pt: f32) {
        if let Some(c) = self.containers.get_mut(id.0) {
            if pt.is_finite() {
                c.spacing = pt.max(0.0);
            }
        }
    }

    pub fn add(&mut self, glass: Glass) -> GlassId {
        let id = GlassId(self.glasses.len());
        let clean_rect = glass.rect.sanitize(Rect::new(0.0, 0.0, 48.0, 48.0));
        self.glasses.push(GlassNode {
            id,
            current_rect: clean_rect,
            target_rect: clean_rect,
            vel_x: 0.0,
            vel_y: 0.0,
            vel_w: 0.0,
            vel_h: 0.0,
            shape: glass.shape,
            material: glass.material.clone(),
            target_material: glass.material,
            material_blend: 1.0,
            material_vel: 0.0,
            container: glass.container,
            z: glass.z,
            interactive: glass.interactive,
            spring: Spring::new(0.36, 0.15),
            press_glow: 0.0,
            press_vel: 0.0,
            shrink_target: None,
            alive: true,
        });
        id
    }

    pub fn set_frame(&mut self, id: GlassId, rect: Rect) {
        if let Some(node) = self.glasses.get_mut(id.0) {
            let sanitized = rect.sanitize(node.target_rect);
            node.target_rect = sanitized;
            if !self.physics {
                node.current_rect = sanitized;
                node.vel_x = 0.0;
                node.vel_y = 0.0;
                node.vel_w = 0.0;
                node.vel_h = 0.0;
            }
        }
    }

    pub fn place(&mut self, id: GlassId, rect: Rect) {
        if let Some(node) = self.glasses.get_mut(id.0) {
            let sanitized = rect.sanitize(node.current_rect);
            node.current_rect = sanitized;
            node.target_rect = sanitized;
            node.vel_x = 0.0;
            node.vel_y = 0.0;
            node.vel_w = 0.0;
            node.vel_h = 0.0;
        }
    }

    pub fn remove(&mut self, id: GlassId) {
        if let Some(node) = self.glasses.get_mut(id.0) {
            node.alive = false;
        }
    }

    pub fn set_material(&mut self, id: GlassId, material: Material) {
        if let Some(node) = self.glasses.get_mut(id.0) {
            node.material = material.clone();
            node.target_material = material;
            node.material_blend = 1.0;
            node.material_vel = 0.0;
        }
    }

    pub fn set_material_with(&mut self, id: GlassId, material: Material, spring: Spring) {
        if let Some(node) = self.glasses.get_mut(id.0) {
            node.material = node.material.blend(&node.target_material, node.material_blend);
            node.target_material = material;
            node.material_blend = 0.0;
            node.material_vel = 0.0;
            node.spring = spring;
        }
    }

    pub fn expand_from(&mut self, source: GlassId, mut glass: Glass, spring: Spring) -> GlassId {
        let (start_rect, source_container) = self
            .glasses
            .get(source.0)
            .map(|s| (s.current_rect, s.container))
            .unwrap_or((glass.rect, glass.container));
        let target_rect = glass.rect.sanitize(start_rect);
        if glass.container.is_none() {
            glass.container = source_container;
        }
        glass.rect = start_rect;
        let new_id = self.add(glass);
        if let Some(node) = self.glasses.get_mut(new_id.0) {
            node.target_rect = target_rect;
            node.spring = spring;
            if !self.physics {
                node.current_rect = target_rect;
            }
        }
        new_id
    }

    pub fn collapse_into(&mut self, id: GlassId, target: GlassId, spring: Spring) {
        let target_rect = self
            .glasses
            .get(target.0)
            .map(|t| t.current_rect)
            .unwrap_or(Rect::new(0.0, 0.0, 0.0, 0.0));
        if let Some(node) = self.glasses.get_mut(id.0) {
            node.target_rect = target_rect;
            node.spring = spring;
            node.shrink_target = Some(target);
            if !self.physics {
                node.current_rect = target_rect;
                node.alive = false;
            }
        }
    }

    pub fn morph_from(&mut self, button: GlassId, mut glass: Glass, spring: Spring) -> GlassId {
        let start_rect = self
            .glasses
            .get(button.0)
            .map(|b| b.current_rect)
            .unwrap_or(glass.rect);
        let target_rect = glass.rect.sanitize(start_rect);
        glass.container = None;
        glass.rect = start_rect;
        let new_id = self.add(glass);
        if let Some(node) = self.glasses.get_mut(new_id.0) {
            node.target_rect = target_rect;
            node.spring = spring;
            if !self.physics {
                node.current_rect = target_rect;
            }
        }
        new_id
    }

    pub fn shrink_into(&mut self, menu: GlassId, button: GlassId, spring: Spring) {
        let target_rect = self
            .glasses
            .get(button.0)
            .map(|b| b.current_rect)
            .unwrap_or(Rect::new(0.0, 0.0, 0.0, 0.0));
        if let Some(node) = self.glasses.get_mut(menu.0) {
            node.container = None;
            node.target_rect = target_rect;
            node.spring = spring;
            node.shrink_target = Some(button);
            if !self.physics {
                node.current_rect = target_rect;
                node.alive = false;
            }
        }
    }

    pub fn pointer_down(&mut self, p: (f32, f32)) {
        if !p.0.is_finite() || !p.1.is_finite() {
            return;
        }
        self.pointer_pos = Some(p);
        self.pointer_vel = (0.0, 0.0);
        let mut best_idx: Option<usize> = None;
        let mut best_z = i32::MIN;
        for (idx, g) in self.glasses.iter().enumerate() {
            if g.alive && g.interactive && g.current_rect.contains(p.0, p.1) && g.z >= best_z {
                best_z = g.z;
                best_idx = Some(idx);
            }
        }
        if let Some(idx) = best_idx {
            if let Some(g) = self.glasses.get_mut(idx) {
                g.press_glow = if self.physics { g.press_glow.max(0.35) } else { 1.0 };
            }
        }
    }

    pub fn pointer_move(&mut self, p: (f32, f32)) {
        if !p.0.is_finite() || !p.1.is_finite() {
            return;
        }
        if let Some(prev) = self.pointer_pos {
            self.pointer_vel = (p.0 - prev.0, p.1 - prev.1);
        }
        self.pointer_pos = Some(p);
    }

    pub fn pointer_up(&mut self) {
        self.pointer_pos = None;
        self.pointer_vel = (0.0, 0.0);
    }

    pub fn set_light_angle(&mut self, angle_rad: f32) {
        if angle_rad.is_finite() {
            self.appearance.light_angle_rad = angle_rad;
        }
    }

    pub fn update(&mut self, dt: f32) -> bool {
        let clean_dt = if dt.is_finite() && dt > 0.0 {
            dt.min(0.1)
        } else {
            0.0
        };
        if !self.physics {
            for g in &mut self.glasses {
                if !g.alive {
                    continue;
                }
                g.current_rect = g.target_rect;
                g.vel_x = 0.0;
                g.vel_y = 0.0;
                g.vel_w = 0.0;
                g.vel_h = 0.0;
                g.material_blend = 1.0;
                g.material_vel = 0.0;
                if g.shrink_target.is_some() {
                    g.alive = false;
                }
            }
            return false;
        }

        let reduce_motion = self.appearance.reduce_motion;
        let pointer = self.pointer_pos;
        let mut any_animating = false;

        for idx in 0..self.glasses.len() {
            if !self.glasses[idx].alive {
                continue;
            }
            if let Some(target_id) = self.glasses[idx].shrink_target {
                if let Some(t_node) = self.glasses.get(target_id.0) {
                    self.glasses[idx].target_rect = t_node.current_rect;
                }
            }

            let g = &mut self.glasses[idx];
            let spring = g.spring;
            let (nx, vx, mx) =
                spring.step_scalar(g.current_rect.x, g.vel_x, g.target_rect.x, clean_dt, reduce_motion);
            let (ny, vy, my) =
                spring.step_scalar(g.current_rect.y, g.vel_y, g.target_rect.y, clean_dt, reduce_motion);
            let (nw, vw, mw) = spring.step_scalar(
                g.current_rect.width,
                g.vel_w,
                g.target_rect.width,
                clean_dt,
                reduce_motion,
            );
            let (nh, vh, mh) = spring.step_scalar(
                g.current_rect.height,
                g.vel_h,
                g.target_rect.height,
                clean_dt,
                reduce_motion,
            );

            g.current_rect = Rect::new(nx, ny, nw.max(0.0), nh.max(0.0));
            g.vel_x = vx;
            g.vel_y = vy;
            g.vel_w = vw;
            g.vel_h = vh;

            let (mb, mv, mm) =
                spring.step_scalar(g.material_blend, g.material_vel, 1.0, clean_dt, reduce_motion);
            g.material_blend = mb.clamp(0.0, 1.0);
            g.material_vel = mv;

            let target_glow = match pointer {
                Some((px, py)) if g.interactive && g.current_rect.contains(px, py) => 1.0,
                _ => 0.0,
            };
            let press_spring = Spring::new(0.22, 0.10);
            let (pg, pv, mp) =
                press_spring.step_scalar(g.press_glow, g.press_vel, target_glow, clean_dt, reduce_motion);
            g.press_glow = pg.clamp(0.0, 1.0);
            g.press_vel = pv;

            let settled = !(mx || my || mw || mh);
            if settled && g.shrink_target.is_some() {
                g.alive = false;
            } else if mx || my || mw || mh || mm || mp {
                any_animating = true;
            }
        }

        any_animating
    }

    pub fn build_frame_packet(
        &self,
        viewport_w: f32,
        viewport_h: f32,
        backdrop_luma: f32,
    ) -> LiquidGlassFramePacket {
        if viewport_w <= 0.0 || viewport_h <= 0.0 || !viewport_w.is_finite() || !viewport_h.is_finite()
        {
            return LiquidGlassFramePacket {
                animating: false,
                glass_on_glass: self.glass_on_glass,
                items: Vec::new(),
                liquid_bridges: Vec::new(),
            };
        }

        let mut items = Vec::with_capacity(self.glasses.len());
        for g in &self.glasses {
            if !g.alive {
                continue;
            }
            let active_mat = g.material.blend(&g.target_material, g.material_blend);
            let surface = resolve_surface_material(
                &active_mat,
                g.shape,
                g.current_rect.width,
                g.current_rect.height,
                &self.appearance,
                backdrop_luma,
            );
            items.push(LiquidGlassItemSnapshot {
                id: g.id.0,
                rect: g.current_rect,
                z: g.z,
                press_glow: g.press_glow,
                surface,
            });
        }

        let mut bridges = Vec::new();
        for c in &self.containers {
            if c.spacing <= 0.0 {
                continue;
            }
            let members: Vec<&GlassNode> = self
                .glasses
                .iter()
                .filter(|g| g.alive && g.container == Some(c.id))
                .collect();
            for i in 0..members.len() {
                for j in (i + 1)..members.len() {
                    let a = members[i];
                    let b = members[j];
                    let gap = a.current_rect.edge_gap(&b.current_rect);
                    if gap < c.spacing {
                        let fuse_factor = ((c.spacing - gap.max(0.0)) / c.spacing).clamp(0.0, 1.0);
                        let (ax, ay) = a.current_rect.center();
                        let (bx, by) = b.current_rect.center();
                        let min_h = a.current_rect.height.min(b.current_rect.height);
                        let neck_thickness_pt = if gap <= c.spacing * 0.5 {
                            min_h * (0.32 + 0.46 * fuse_factor)
                        } else {
                            min_h * 0.18 * fuse_factor
                        };
                        bridges.push(LiquidNeckBridge {
                            a_id: a.id.0,
                            b_id: b.id.0,
                            gap_pt: gap,
                            fuse_factor,
                            neck_center_x: (ax + bx) * 0.5,
                            neck_center_y: (ay + by) * 0.5,
                            neck_thickness_pt,
                        });
                    }
                }
            }
        }

        LiquidGlassFramePacket {
            animating: false,
            glass_on_glass: self.glass_on_glass,
            items,
            liquid_bridges: bridges,
        }
    }
}

pub fn evaluate_nav_bar_liquid_state(
    tab_count: usize,
    current_index: usize,
    target_index: usize,
    progress: f32,
    pointer_x_norm: f32,
    is_pressed: bool,
    spacing_pt: f32,
    transparency: f32,
    dark_mode: bool,
) -> NavBarLiquidState {
    let count = tab_count.max(1) as f32;
    let slot_w = 1.0 / count;
    let cur = (current_index.min(tab_count.saturating_sub(1)) as f32 + 0.5) * slot_w;
    let tgt = (target_index.min(tab_count.saturating_sub(1)) as f32 + 0.5) * slot_w;
    let p = if progress.is_finite() {
        progress.clamp(0.0, 1.0)
    } else {
        1.0
    };

    let base_x = cur + (tgt - cur) * p;
    let lens_x_fraction = if is_pressed && pointer_x_norm.is_finite() {
        base_x * 0.35 + pointer_x_norm.clamp(slot_w * 0.5, 1.0 - slot_w * 0.5) * 0.65
    } else {
        base_x
    };

    let mid_flight = (4.0 * p * (1.0 - p)).clamp(0.0, 1.0);
    let press_boost = if is_pressed { 0.16 } else { 0.0 };
    let lens_width_fraction = (slot_w * (1.0 + 0.34 * mid_flight + press_boost)).clamp(slot_w * 0.8, 0.65);
    let lens_scale_y = 1.0 + 0.08 * press_boost - 0.05 * mid_flight;

    let nearest_slot_center = ((lens_x_fraction * count).round() - 0.5).clamp(0.0, count - 1.0) * slot_w + 0.5 * slot_w;
    let slot_dist_pt = ((lens_x_fraction - nearest_slot_center).abs() * 360.0).max(0.0);
    let eff_spacing = spacing_pt.max(8.0);
    let liquid_neck_strength = if slot_dist_pt < eff_spacing {
        ((eff_spacing - slot_dist_pt) / eff_spacing).clamp(0.0, 1.0) * (0.45 + 0.55 * mid_flight)
    } else {
        0.0
    };

    let neighbor_pull_offset = (lens_x_fraction - nearest_slot_center) * 18.0 * liquid_neck_strength;
    let press_glow = if is_pressed {
        1.0
    } else {
        (mid_flight * 0.65).clamp(0.0, 1.0)
    };

    let appearance = Appearance {
        transparency: transparency.clamp(0.0, 1.0),
        dark_mode,
        reduce_transparency: false,
        increase_contrast: false,
        reduce_motion: false,
        adapt_to_content: true,
        light_angle_rad: -0.7853982,
    };
    let backdrop_luma = if dark_mode { 0.08 } else { 0.88 };
    let bar_surface = resolve_surface_material(
        &Material::clear_bar(),
        Shape::Rounded(34.0),
        368.0,
        68.0,
        &appearance,
        backdrop_luma,
    );
    let lens_surface = resolve_surface_material(
        &Material::tinted(Color::MINT),
        Shape::Rounded(26.0),
        84.0,
        52.0,
        &appearance,
        backdrop_luma,
    );

    NavBarLiquidState {
        lens_x_fraction,
        lens_width_fraction,
        lens_scale_y,
        liquid_neck_strength,
        neighbor_pull_offset,
        press_glow,
        bar_surface,
        lens_surface,
    }
}

pub fn generate_wgsl_shader() -> &'static str {
    r#"struct GlassUniforms {
    resolution: vec2<f32>,
    rect_center: vec2<f32>,
    rect_half: vec2<f32>,
    corner_radius: f32,
    superellipse_n: f32,
    refraction: f32,
    dispersion: f32,
    splay: f32,
    specular: f32,
    face_color: vec4<f32>,
    tint_color: vec4<f32>,
    light_dir: vec2<f32>,
    press_glow: f32,
    neck_strength: f32,
};

@group(0) @binding(0) var<uniform> u: GlassUniforms;
@group(0) @binding(1) var backdrop_tex: texture_2d<f32>;
@group(0) @binding(2) var frost_tex: texture_2d<f32>;
@group(0) @binding(3) var glass_sampler: sampler;

fn squircle_sdf(p: vec2<f32>, center: vec2<f32>, half_ext: vec2<f32>, r: f32, n: f32) -> f32 {
    let q = abs(p - center) - (half_ext - vec2<f32>(r));
    let o = max(q, vec2<f32>(0.0));
    var corner = length(o);
    if (o.x > 0.0 && o.y > 0.0 && r > 0.0) {
        corner = pow(pow(o.x / r, n) + pow(o.y / r, n), 1.0 / n) * r;
    }
    return corner + min(max(q.x, q.y), 0.0) - r;
}

@fragment
fn fs_main(@builtin(position) frag_coord: vec4<f32>) -> @location(0) vec4<f32> {
    let p = frag_coord.xy;
    let d = squircle_sdf(p, u.rect_center, u.rect_half, u.corner_radius, u.superellipse_n);
    if (d > 1.0) {
        discard;
    }
    let eps = 1.25;
    let gx = squircle_sdf(p + vec2<f32>(eps, 0.0), u.rect_center, u.rect_half, u.corner_radius, u.superellipse_n) - d;
    let gy = squircle_sdf(p + vec2<f32>(0.0, eps), u.rect_center, u.rect_half, u.corner_radius, u.superellipse_n) - d;
    let normal = normalize(vec2<f32>(gx, gy) + vec2<f32>(1e-5));
    let bevel = clamp(1.0 + d / max(u.splay, 1.0), 0.0, 1.0);
    let snell_bend = bevel * bevel * (3.0 - 2.0 * bevel) * (u.refraction / max(u.resolution.x, 1.0));
    let uv = p / u.resolution;
    let disp = normal * (u.dispersion / max(u.resolution.x, 1.0)) * bevel;
    let uv_r = clamp(uv - normal * snell_bend - disp, vec2<f32>(0.001), vec2<f32>(0.999));
    let uv_g = clamp(uv - normal * snell_bend, vec2<f32>(0.001), vec2<f32>(0.999));
    let uv_b = clamp(uv - normal * snell_bend + disp, vec2<f32>(0.001), vec2<f32>(0.999));
    let col_r = textureSample(frost_tex, glass_sampler, uv_r).r;
    let col_g = textureSample(frost_tex, glass_sampler, uv_g).g;
    let col_b = textureSample(frost_tex, glass_sampler, uv_b).b;
    var refracted = vec3<f32>(col_r, col_g, col_b);
    refracted = mix(refracted, u.face_color.rgb, u.face_color.a);
    refracted = mix(refracted, u.tint_color.rgb, u.tint_color.a * 0.55);
    let rim_align = max(dot(normal, u.light_dir), 0.0);
    let rim_band = exp(-abs(d) * 0.65) * u.specular * (0.45 + 0.55 * rim_align);
    let final_rgb = clamp(refracted + vec3<f32>(rim_band + u.press_glow * 0.14), vec3<f32>(0.0), vec3<f32>(1.0));
    let edge_aa = clamp(0.5 - d, 0.0, 1.0);
    return vec4<f32>(final_rgb, edge_aa);
}
"#
}

pub fn generate_agsl_shader() -> &'static str {
    r#"uniform float2 uResolution;
uniform float4 uFaceColor;
uniform float4 uTintColor;
uniform float4 uRimLightColor;
uniform float uCornerRadius;
uniform float uSuperellipseN;
uniform float uRefraction;
uniform float uDispersion;
uniform float uSplay;
uniform float uSpecular;
uniform float uPressGlow;
uniform float2 uLightDir;
uniform float2 uPointerPos;

float squircleSdf(float2 p, float2 center, float2 halfExt, float r, float n) {
    float2 q = abs(p - center) - (halfExt - float2(r));
    float2 o = max(q, float2(0.0));
    float corner = length(o);
    if (o.x > 0.0 && o.y > 0.0 && r > 0.0) {
        corner = pow(pow(o.x / r, n) + pow(o.y / r, n), 1.0 / n) * r;
    }
    return corner + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 res = max(uResolution, float2(1.0));
    float2 center = res * 0.5;
    float2 halfExt = center - float2(1.0);
    float r = clamp(uCornerRadius, 2.0, min(halfExt.x, halfExt.y));
    float d = squircleSdf(fragCoord, center, halfExt, r, uSuperellipseN);
    if (d > 1.0) {
        return half4(0.0);
    }
    float eps = 1.5;
    float gx = squircleSdf(fragCoord + float2(eps, 0.0), center, halfExt, r, uSuperellipseN) - d;
    float gy = squircleSdf(fragCoord + float2(0.0, eps), center, halfExt, r, uSuperellipseN) - d;
    float2 normal = normalize(float2(gx, gy) + float2(0.0001));
    float bevel = clamp(1.0 + d / max(uSplay, 2.0), 0.0, 1.0);
    float snell = bevel * bevel * (3.0 - 2.0 * bevel);
    float dispShift = (uDispersion * 0.0045) * snell;
    float3 prism = float3(
        max(dot(normal, float2(0.86, -0.50)), 0.0) * dispShift,
        max(dot(normal, uLightDir), 0.0) * dispShift * 0.65,
        max(dot(normal, float2(-0.86, 0.50)), 0.0) * dispShift * 1.25
    );
    float rimAlign = clamp(dot(normal, uLightDir) * 0.5 + 0.5, 0.0, 1.0);
    float outerRim = exp(-abs(d) * 0.55) * uSpecular * (0.35 + 0.65 * rimAlign);
    float innerDodge = snell * 0.16 * (1.0 - rimAlign * 0.4);
    float ptrDist = length(fragCoord - uPointerPos) / max(max(res.x, res.y), 1.0);
    float touchGlow = exp(-ptrDist * 3.8) * uPressGlow * 0.22;
    float3 baseRgb = mix(uFaceColor.rgb, uTintColor.rgb, clamp(uTintColor.a, 0.0, 0.85));
    float3 litRgb = baseRgb + prism + uRimLightColor.rgb * outerRim + float3(innerDodge + touchGlow);
    float alpha = clamp(uFaceColor.a + outerRim * 0.55 + snell * 0.18 + touchGlow * 0.35, 0.08, 0.96);
    float edgeAa = clamp(0.75 - d, 0.0, 1.0);
    return half4(half3(clamp(litRgb, float3(0.0), float3(1.0)) * edgeAa), half(alpha * edgeAa));
}
"#
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_apple_kit_presets_and_transparency_slider() {
        let mut app = Appearance::default();
        app.dark_mode = true;
        app.transparency = 0.5;
        let default_dark = resolve_surface_material(
            &Material::regular(),
            Shape::Rounded(24.0),
            240.0,
            240.0,
            &app,
            0.15,
        );
        app.transparency = 1.0;
        let tinted_dark = resolve_surface_material(
            &Material::regular(),
            Shape::Rounded(24.0),
            240.0,
            240.0,
            &app,
            0.15,
        );
        assert!(tinted_dark.face_opacity > default_dark.face_opacity);
        assert!(tinted_dark.wcag_contrast_ratio >= 7.0);
        assert!(tinted_dark.frost_radius_px >= default_dark.frost_radius_px);

        app.dark_mode = false;
        app.transparency = 0.5;
        let default_light = resolve_surface_material(
            &Material::regular(),
            Shape::Rounded(24.0),
            260.0,
            260.0,
            &app,
            0.12,
        );
        assert!(!default_light.is_dark_face);
        assert!(default_light.wcag_contrast_ratio >= 6.5);
    }

    #[test]
    fn test_scene_springs_merging_and_bad_input() {
        let mut scene = Scene::new();
        let toolbar = scene.add_container(16.0, 0);
        let b1 = scene.add(
            Glass::new(Rect::new(20.0, 24.0, 52.0, 52.0))
                .interactive()
                .container(toolbar),
        );
        let b2 = scene.add(
            Glass::new(Rect::new(78.0, 24.0, 52.0, 52.0))
                .interactive()
                .container(toolbar),
        );

        let packet = scene.build_frame_packet(390.0, 844.0, 0.1);
        assert_eq!(packet.items.len(), 2);
        assert_eq!(packet.liquid_bridges.len(), 1);
        assert!(packet.liquid_bridges[0].fuse_factor > 0.5);

        scene.set_frame(b1, Rect::new(f32::NAN, f32::INFINITY, -10.0, 52.0));
        assert!(!scene.update(f32::NAN));

        let expanded = scene.expand_from(
            b2,
            Glass::new(Rect::new(40.0, 96.0, 300.0, 240.0)).shape(Shape::Rounded(28.0)),
            Spring::EMERGE,
        );
        assert!(scene.update(0.016));
        scene.collapse_into(expanded, b2, Spring::RECEDE);
        for _ in 0..120 {
            if !scene.update(0.016) {
                break;
            }
        }
        assert!(!scene.update(0.016));
    }

    #[test]
    fn test_shaders_and_nav_bar_evaluator() {
        let wgsl = generate_wgsl_shader();
        let agsl = generate_agsl_shader();
        assert!(wgsl.contains("squircle_sdf"));
        assert!(agsl.contains("squircleSdf"));

        let nav = evaluate_nav_bar_liquid_state(5, 0, 1, 0.5, 0.25, true, 16.0, 0.5, true);
        assert!(nav.lens_width_fraction > 0.20);
        assert!(nav.press_glow > 0.5);
    }
}
