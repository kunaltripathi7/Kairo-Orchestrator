from PIL import Image, ImageChops

def trim_with_tolerance(image_path, tolerance=30):
    im = Image.open(image_path).convert("RGB")
    bg = Image.new(im.mode, im.size, im.getpixel((0,0)))
    diff = ImageChops.difference(im, bg)
    diff = ImageChops.add(diff, diff, 2.0, -tolerance)
    bbox = diff.getbbox()
    if bbox:
        cropped = im.crop(bbox)
        cropped.save(image_path)
        print(f"Cropped {image_path} to {bbox}")
    else:
        print(f"Could not crop {image_path}")

trim_with_tolerance('/home/vael/stuff/Development/Kairo-Orchestrator/kairo-ui/public/logo-1.png')
trim_with_tolerance('/home/vael/stuff/Development/Kairo-Orchestrator/kairo-ui/public/logo-2.png')
