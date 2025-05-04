package entities;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SongObject {
    private String name;
    private String artists;
    private String albumImageUrl;
    private String uri;
    private boolean isInQueue;
}
