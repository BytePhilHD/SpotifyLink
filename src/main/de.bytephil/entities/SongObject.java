package entities;

import com.fasterxml.jackson.annotation.JsonProperty;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
public class SongObject {

    @JsonProperty("name")
    private String name;

    @JsonProperty("artists")
    private String artists;

    @JsonProperty("cover")
    private String cover;

    @JsonProperty("uri")
    private String uri;

    @JsonProperty("played")
    private boolean played;

}
