package net.osmand.osm.edit;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.osmand.data.Amenity;
import net.osmand.data.AmenityType;
import net.osmand.data.Building;
import net.osmand.data.City;
import net.osmand.data.City.CityType;
import net.osmand.data.LatLon;
import net.osmand.data.MapObject;
import net.osmand.data.TransportStop;
import net.osmand.osm.MapRenderingTypes;
import net.osmand.osm.edit.OSMSettings.OSMTagKey;
import net.osmand.util.Algorithms;

public class EntityParser {
	
	public static void parseMapObject(MapObject mo, Entity e) {
		mo.setId(e.getId());
		if(mo instanceof Amenity) {
			mo.setId((e.getId() << 1) + ((e instanceof Node) ? 0 : 1));
		}
		if (mo.getName().length() == 0) {
			mo.setName(e.getTag(OSMTagKey.NAME));
		}
		if (mo.getEnName().length() == 0) {
			mo.setEnName(e.getTag(OSMTagKey.NAME_EN));
			if (mo.getName().length() == 0) {
				mo.setName(mo.getEnName());
			}
		}
		if (mo.getLocation() == null) {
			LatLon l = OsmMapUtils.getCenter(e);
			if (l != null) {
				mo.setLocation(l.getLatitude(), l.getLongitude());
			}
		}
		if (mo.getName().length() == 0) {
			setNameFromOperator(mo, e);
		}
		if (mo.getName().length() == 0) {
			setNameFromRef(mo, e);
		}
	}

	private static void setNameFromRef(MapObject mo, Entity e) {
		String ref = e.getTag(OSMTagKey.REF);
		if(ref != null){
			mo.setName(ref);
		}
	}

	private static void setNameFromOperator(MapObject mo,Entity e) {
		String op = e.getTag(OSMTagKey.OPERATOR);
		if (op == null)
			return;
		String ref = e.getTag(OSMTagKey.REF);
		if (ref != null)
			op += " [" + ref + "]";
		mo.setName(op);
	}
	
	public static Amenity parseAmenity(Entity entity, AmenityType type, String subtype, Map<String, String> tagValues,
			MapRenderingTypes types) {
		Amenity am = new Amenity();
		parseMapObject(am, entity);
		if(tagValues == null) {
			tagValues = entity.getTags();
		}
		am.setType(type);
		am.setSubType(subtype);
		am.setAdditionalInfo(types.getAmenityAdditionalInfo(tagValues, type, subtype));
		String wbs = getWebSiteURL(entity);
		if(wbs != null) {
			am.setAdditionalInfo("website", wbs);
		}
		return am;
	}

	

	private static String getWebSiteURL(Entity entity) {
		String siteUrl = entity.getTag(OSMTagKey.WIKIPEDIA);
		if (siteUrl != null) {
			if (!siteUrl.startsWith("http://")) { //$NON-NLS-1$
				int i = siteUrl.indexOf(':');
				if (i == -1) {
					siteUrl = "http://en.wikipedia.org/wiki/" + siteUrl; //$NON-NLS-1$
				} else {
					siteUrl = "http://" + siteUrl.substring(0, i) + ".wikipedia.org/wiki/" + siteUrl.substring(i + 1); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		} else {
			siteUrl = entity.getTag(OSMTagKey.WEBSITE);
			if (siteUrl == null) {
				siteUrl = entity.getTag(OSMTagKey.URL);
				if (siteUrl == null) {
					siteUrl = entity.getTag(OSMTagKey.CONTACT_WEBSITE);
				}
			}
			if (siteUrl != null && !siteUrl.startsWith("http://") && !siteUrl.startsWith("https://")) {
				siteUrl = "http://" + siteUrl;
			}
		}
		return siteUrl;
	}
	
	public static List<Amenity> parseAmenities(MapRenderingTypes renderingTypes,
			Entity entity, List<Amenity> amenitiesList){
		amenitiesList.clear();
		// it could be collection of amenities
		boolean relation = entity instanceof Relation;
		Collection<Map<String, String>> it = renderingTypes.splitTagsIntoDifferentObjects(entity.getTags());
		for(Map<String, String> tags : it) {
			if (!tags.isEmpty()) {
				boolean purerelation = relation && !"multipolygon".equals(tags.get("type"));
				boolean hasName = !Algorithms.isEmpty(tags.get("name"));
				for (Map.Entry<String, String> e : tags.entrySet()) {
					AmenityType type = purerelation ? renderingTypes.getAmenityTypeForRelation(e.getKey(), e.getValue(), hasName)
							: renderingTypes.getAmenityType(e.getKey(), e.getValue(), hasName );
					if (type != null) {
						String subtype = renderingTypes.getAmenitySubtype(e.getKey(), e.getValue());
						Amenity a = parseAmenity(entity, type, subtype, tags, renderingTypes);
						if (checkAmenitiesToAdd(a, amenitiesList) && !"no".equals(subtype)) {
							amenitiesList.add(a);
						}
					}
				}
			}
		}
		return amenitiesList;
	}
	
	private static boolean checkAmenitiesToAdd(Amenity a, List<Amenity> amenitiesList){
		// check amenity for duplication
		for(Amenity b : amenitiesList){
			if(b.getType() == a.getType() && Algorithms.objectEquals(a.getSubType(), b.getSubType())){
				return false;
			}
		}
		return true;
		
	}
	
	public static Building parseBuilding(Entity e){
		Building b = new Building();
		parseMapObject(b, e);
		// try to extract postcode
		String p = e.getTag(OSMTagKey.ADDR_POSTCODE);
		if(p == null) {
			p = e.getTag(OSMTagKey.POSTAL_CODE);
		}
		b.setPostcode(p);
		return b;
	}
	
	public static City parseCity(Node el) {
		return parseCity(el, CityType.valueFromString(el.getTag(OSMTagKey.PLACE)));
	}
	
	public static City parseCity(Entity el, CityType t) {
		if(t == null) {
			return null;
		}
		City c = new City(t);
		parseMapObject(c, el);
		String isin = el.getTag(OSMTagKey.IS_IN);
		isin = isin != null ? isin.toLowerCase() : null;
		c.setIsin(isin);
		return c;
	}
	
	
	public static OsmTransportRoute parserRoute(Relation r, String ref){
		OsmTransportRoute rt = new OsmTransportRoute();
		parseMapObject(rt, r);
		rt.setRef(ref);
		return rt;
	}
	
	public static TransportStop parseTransportStop(Entity e){
		TransportStop st = new TransportStop();
		parseMapObject(st, e);
		return st;
	}

}
